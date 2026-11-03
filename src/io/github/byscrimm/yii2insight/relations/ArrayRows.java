package io.github.byscrimm.yii2insight.relations;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.Function;
import io.github.byscrimm.yii2insight.common.*;
import java.util.*;
import java.util.regex.*;

/**
 * Keys of {@code asArray()} ActiveQuery rows. Only statically visible structure is reported:
 * a dynamic select, ambiguous data flow or unknown query method yields no row.
 */
public final class ArrayRows {
    /** Row keys; a value is the nested row of an eagerly loaded relation, or null for a scalar. */
    public record Row(Map<String,Row> keys, boolean collection) {
        Row element() { return collection ? new Row(keys,false) : null; }
    }
    private static final Set<String> CHAIN = Set.of("find","where","andWhere","orWhere","filterWhere","andFilterWhere","orFilterWhere",
        "orderBy","addOrderBy","groupBy","addGroupBy","having","andHaving","orHaving","limit","offset","select","addSelect","distinct",
        "indexBy","alias","with","joinWith","innerJoinWith","asArray","cache","noCache");
    // Yii splits "expr AS name" and "expr name" the same way (Query::getUniqueColumns / buildSelect).
    private static final Pattern ALIASED = Pattern.compile("^(.*?)(?i:\\s+as\\s+|\\s+)([\\w\\-.]+)$", Pattern.DOTALL);
    private static final Pattern COLUMN = Pattern.compile("^(?:(?:\\{\\{%?)?([\\w\\-]+)(?:%?}})?\\.)?(?:\\[\\[)?([\\w\\-]+|\\*)(?:]])?$");
    private static final int MAX_DEPTH = 24;

    private final YiiModelResolver resolver;
    private final QueryAliases aliases;
    public ArrayRows(YiiModelResolver resolver) { this.resolver = resolver; this.aliases = new QueryAliases(resolver); }

    /** The row an array expression holds, e.g. {@code $city} after {@code ->asArray()->one()}. */
    public Row row(PsiElement expression) { return row(expression, 0); }
    private Row row(PsiElement expression, int depth) {
        Row value = value(expression, depth);
        return value == null || value.collection ? null : value;
    }

    private Row value(PsiElement expression, int depth) {
        if (expression == null || depth > MAX_DEPTH) return null;
        ProgressManager.checkCanceled();
        if (expression instanceof ParenthesizedExpression parenthesized) return value(parenthesized.getArgument(), depth+1);
        if (expression instanceof Variable variable) {
            Row loop = foreachElement(variable, depth);
            if (loop != null) return loop;
            return value(LocalPhpValues.resolve(variable), depth+1);
        }
        if (expression instanceof ArrayAccessExpression access) {
            Row owner = value(access.getValue(), depth+1);
            if (owner == null) return null;
            if (owner.collection) return owner.element();
            PsiElement index = access.getIndex() == null ? null : access.getIndex().getValue();
            String key = PhpArrays.string(index);
            return key == null ? null : owner.keys.get(key);
        }
        if (expression instanceof MethodReference call && ("one".equals(call.getName()) || "all".equals(call.getName()))) {
            Map<String,Row> keys = query(call);
            return keys == null ? null : new Row(keys, "all".equals(call.getName()));
        }
        return null;
    }

    /** {@code foreach ($rows as $row)}: the loop value, unless reassigned inside the loop. */
    private Row foreachElement(Variable variable, int depth) {
        for (ForeachStatement loop = PsiTreeUtil.getParentOfType(variable,ForeachStatement.class); loop != null;
                loop = PsiTreeUtil.getParentOfType(loop,ForeachStatement.class)) {
            if (!(loop.getValue() instanceof Variable value) || !variable.getName().equals(value.getName())) continue;
            if (PsiTreeUtil.getParentOfType(loop,Function.class) != PsiTreeUtil.getParentOfType(variable,Function.class)) return null;
            for (AssignmentExpression assignment : PsiTreeUtil.findChildrenOfType(loop,AssignmentExpression.class))
                if (assignment.getVariable() instanceof Variable target && variable.getName().equals(target.getName())) return null;
            Row collection = value(loop.getArray(), depth+1);
            return collection == null ? null : collection.element();
        }
        return null;
    }

    /** Row keys of an {@code asArray()} query ending at {@code one()}/{@code all()}; null when not an array query or unknown. */
    private Map<String,Row> query(MethodReference terminal) {
        List<MethodReference> chain = new ArrayList<>();
        PsiElement current = terminal.getClassReference();
        Set<PsiElement> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < MAX_DEPTH && current != null && seen.add(current); i++) {
            if (current instanceof ParenthesizedExpression parenthesized) current = parenthesized.getArgument();
            else if (current instanceof Variable variable) current = LocalPhpValues.resolve(variable);
            else if (current instanceof MethodReference ref) {
                if (ref.getName() == null || !CHAIN.contains(ref.getName())) return null;
                chain.add(ref);
                if (ref.getClassReference() instanceof ClassReference) break;
                current = ref.getClassReference();
            } else return null;
        }
        Collections.reverse(chain);
        List<PhpClass> models = resolver.models(terminal);
        if (models.isEmpty() || chain.isEmpty() || !"find".equals(chain.getFirst().getName())) return null;
        Boolean array = null;
        for (MethodReference ref : chain) if ("asArray".equals(ref.getName())) {
            PsiElement argument = PhpArguments.get(ref,"value",0);
            if (argument == null) array = true;
            else if (argument instanceof ConstantReference constant && constant.getName() != null)
                array = "true".equalsIgnoreCase(constant.getName()) ? Boolean.TRUE : "false".equalsIgnoreCase(constant.getName()) ? Boolean.FALSE : null;
            else return null;
            if (array == null) return null;
        }
        if (!Boolean.TRUE.equals(array)) return null;
        Map<String,Row> keys = null;
        for (MethodReference ref : chain) {
            if ("select".equals(ref.getName())) keys = new LinkedHashMap<>();
            if ("select".equals(ref.getName()) || "addSelect".equals(ref.getName())) {
                if (keys == null) keys = new LinkedHashMap<>();
                if (!select(ref, models, keys)) return null;
            }
        }
        if (keys == null) keys = attributes(models);
        for (MethodReference ref : chain) relations(ref, models, keys);
        return keys;
    }

    private boolean select(MethodReference ref, List<PhpClass> models, Map<String,Row> keys) {
        PsiElement columns = PhpArguments.get(ref,"columns",0);
        if (columns instanceof StringLiteralExpression literal) {
            for (String item : literal.getContents().trim().split("\\s*,\\s*")) if (!item.isEmpty()) item(item, ref, models, keys);
            return true;
        }
        if (!(columns instanceof ArrayCreationExpression array)) return false;
        for (PsiElement child : array.getChildren()) {
            if (child instanceof ArrayHashElement hash) {
                String key = PhpArrays.string(hash.getKey());
                if (key != null) keys.put(key, null);
                else if (!(hash.getKey() instanceof PhpPsiElement number) || !number.getText().matches("\\d+")) continue;
                else if (PhpArrays.string(hash.getValue()) instanceof String value) item(value, ref, models, keys);
            } else if (child instanceof PhpPsiElement wrapper && PhpArrays.string(wrapper.getFirstPsiChild()) instanceof String value)
                item(value, ref, models, keys);
        }
        return true;
    }

    /** One select column: {@code city.id} → {@code id}, {@code city.id AS cid} → {@code cid}, {@code *}/{@code city.*} → attributes. */
    private void item(String text, MethodReference ref, List<PhpClass> models, Map<String,Row> keys) {
        text = text.trim();
        Matcher column = COLUMN.matcher(text);
        if (column.matches()) {
            if (!"*".equals(column.group(2))) { keys.put(column.group(2), null); return; }
            List<PhpClass> source = column.group(1) == null ? models : aliases.visible(ref).get(column.group(1));
            if (source != null) attributes(source).forEach(keys::putIfAbsent);
            return;
        }
        Matcher aliased = ALIASED.matcher(text);
        if (aliased.matches() && !aliased.group(2).contains(".")) keys.put(aliased.group(2), null);
        // Other expressions have driver-specific result names and are skipped.
    }

    private Map<String,Row> attributes(List<PhpClass> models) {
        Map<String,Row> keys = new LinkedHashMap<>();
        for (PhpClass model : models) for (var property : aliases.attributes(model)) keys.putIfAbsent(property.getName(), null);
        return keys;
    }

    /** Eagerly loaded relations ({@code with}, eager {@code joinWith}) add a nested row or row collection. */
    private void relations(MethodReference ref, List<PhpClass> models, Map<String,Row> keys) {
        String name = ref.getName();
        PsiElement with;
        if ("with".equals(name)) with = null;
        else if ("joinWith".equals(name) || "innerJoinWith".equals(name)) {
            PsiElement eager = PhpArguments.get(ref,"eagerLoading",1);
            if (eager != null && !(eager instanceof ConstantReference constant && "true".equalsIgnoreCase(constant.getName()))) return;
            with = PhpArguments.get(ref,"with",0);
        } else return;
        List<PsiElement> specs = new ArrayList<>();
        for (PsiElement argument : with == null ? ref.getParameters() : new PsiElement[]{with}) {
            if (argument instanceof ArrayCreationExpression array) {
                for (PsiElement child : array.getChildren()) {
                    if (child instanceof ArrayHashElement hash) specs.add(hash.getKey());
                    else if (child instanceof PhpPsiElement wrapper) specs.add(wrapper.getFirstPsiChild());
                }
            } else specs.add(argument);
        }
        for (PsiElement spec : specs) {
            String[] parsed = QueryAliases.spec(PhpArrays.string(spec));
            if (parsed == null) continue;
            String first = parsed[0].split("\\.")[0];
            for (var relation : resolver.atPath(models,"")) {
                if (!relation.name().equals(first)) continue;
                Boolean many = many(relation.getter());
                Map<String,Row> nested = attributes(relation.targets());
                // A callback on this relation (not on a deeper path segment) may select its own columns.
                ArrayHashElement hash = PsiTreeUtil.getParentOfType(spec,ArrayHashElement.class);
                if (hash != null && hash.getKey() != null && PsiTreeUtil.isAncestor(hash.getKey(),spec,false) && !parsed[0].contains("."))
                    nested = callback(hash.getValue(), relation.targets(), nested);
                keys.put(first, many == null || nested == null ? null : new Row(nested, many));
            }
        }
    }

    /**
     * Keys selected by {@code function ($q) { $q->select(...); }}. Without a select the related
     * model's columns stay; any other use of the parameter or a dynamic select makes them unknown.
     */
    private Map<String,Row> callback(PsiElement value, List<PhpClass> models, Map<String,Row> columns) {
        Function function = value instanceof Function f ? f : PsiTreeUtil.findChildOfType(value,Function.class);
        if (function == null) return null;
        if (function.getParameters().length == 0) return columns;
        String parameter = function.getParameters()[0].getName();
        List<MethodReference> calls = new ArrayList<>();
        for (Variable variable : PsiTreeUtil.findChildrenOfType(function,Variable.class)) {
            ProgressManager.checkCanceled();
            if (!parameter.equals(variable.getName()) || PsiTreeUtil.getParentOfType(variable,Function.class) != function) continue;
            PsiElement element = variable;
            while (element.getParent() instanceof MethodReference ref && ref.getClassReference() == element) {
                if (ref.getName() == null || !CHAIN.contains(ref.getName())) return null;
                calls.add(ref);
                element = ref;
            }
            if (element == variable) return null; // passed, assigned or returned: not statically followed
        }
        calls.sort(Comparator.comparingInt(ref -> ref.getTextRange().getEndOffset()));
        Map<String,Row> keys = null;
        for (MethodReference ref : calls) {
            if ("select".equals(ref.getName())) keys = new LinkedHashMap<>();
            if ("select".equals(ref.getName()) || "addSelect".equals(ref.getName())) {
                if (keys == null) keys = new LinkedHashMap<>();
                if (!select(ref, models, keys)) return null;
            }
            if ("asArray".equals(ref.getName())) return null;
        }
        return keys == null ? columns : keys;
    }

    private static Boolean many(Method getter) {
        boolean one = false, many = false;
        for (MethodReference call : PsiTreeUtil.findChildrenOfType(getter,MethodReference.class)) {
            if ("hasOne".equals(call.getName())) one = true;
            if ("hasMany".equals(call.getName())) many = true;
        }
        return one == many ? null : many;
    }
}
