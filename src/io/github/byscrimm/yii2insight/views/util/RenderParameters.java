package io.github.byscrimm.yii2insight.views.util;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.Function;
import io.github.byscrimm.yii2insight.common.*;
import java.util.*;

/**
 * Parameters passed to a view by {@code render(view, params)}: positional or named arguments,
 * an array literal, {@code compact()} or a local variable holding one of them. A parameter set
 * that cannot be read completely is reported as incomplete; callers must not diagnose it.
 *
 * @param argument the params argument as written in the call, or null when it is absent
 * @param complete every passed name is known
 * @param direct entries are written in the call itself, so quick fixes may edit them
 */
public record RenderParameters(PsiElement argument, List<Entry> entries, boolean complete, boolean direct) {
    /** A passed name; {@code element} is the hash element or compact() string, {@code value} the value expression if known. */
    public record Entry(String name, PsiElement element, PsiElement value) {}

    private static final int MAX_DEPTH = 8;

    public static PsiElement view(ParameterListOwner call) { return PhpArguments.get(call, "view", 0); }

    public static RenderParameters of(ParameterListOwner call) {
        PsiElement argument = PhpArguments.get(call, "params", 1);
        if (argument == null) {
            // render(...$arguments) may pass parameters invisibly.
            boolean spread = Arrays.stream(call.getParameters()).anyMatch(a -> a.getText().startsWith("...")
                || previous(a) != null && "...".equals(previous(a).getText()));
            return new RenderParameters(null, List.of(), !spread, true);
        }
        List<Entry> entries = new ArrayList<>();
        boolean[] direct = {true};
        boolean complete = parse(argument, call, entries, direct, 0);
        return new RenderParameters(argument, List.copyOf(entries), complete, direct[0]);
    }

    public Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        for (Entry entry : entries) names.add(entry.name);
        return names;
    }

    private static boolean parse(PsiElement value, PsiElement use, List<Entry> entries, boolean[] direct, int depth) {
        if (depth > MAX_DEPTH) return false;
        ProgressManager.checkCanceled();
        if (value instanceof ParenthesizedExpression parenthesized) return parse(parenthesized.getArgument(), use, entries, direct, depth+1);
        if (value instanceof ArrayCreationExpression array) {
            boolean complete = true;
            for (PsiElement child : array.getChildren()) {
                if (child instanceof ArrayHashElement hash) {
                    String name = PhpArrays.string(hash.getKey());
                    if (name == null) { complete = false; continue; }
                    entries.add(new Entry(name, hash, hash.getValue()));
                } else if (child instanceof PhpPsiElement && child.getText().trim().startsWith("...")) complete = false;
                // Integer-keyed values are not valid variable names and are skipped by extract().
            }
            return complete;
        }
        if (value instanceof FunctionReference function && !(value instanceof MethodReference) && "compact".equalsIgnoreCase(function.getName())) {
            boolean complete = true;
            for (PsiElement argument : function.getParameters()) {
                if (!(argument instanceof StringLiteralExpression literal)) { complete = false; continue; }
                entries.add(new Entry(literal.getContents(), literal, LocalPhpValues.definition(literal.getContents(), function)));
            }
            return complete;
        }
        if (value instanceof Variable variable && variable.getName() != null) {
            PsiElement definition = LocalPhpValues.definition(variable.getName(), variable);
            if (definition == null || modifiedBefore(variable, definition)) return false;
            direct[0] = false;
            return parse(definition, definition, entries, direct, depth+1);
        }
        return false;
    }

    /** Any other mention of the variable between its definition and this use may change the array. */
    private static boolean modifiedBefore(Variable use, PsiElement definition) {
        Function function = PsiTreeUtil.getParentOfType(use, Function.class);
        PsiElement scope = function != null ? function : use.getContainingFile();
        int from = definition.getTextRange().getEndOffset(), to = use.getTextOffset();
        for (Variable other : PsiTreeUtil.findChildrenOfType(scope, Variable.class)) {
            if (other == use || !use.getName().equals(other.getName()) || PsiTreeUtil.getParentOfType(other, Function.class) != function) continue;
            int offset = other.getTextOffset();
            if (offset > from && offset < to) return true;
        }
        return false;
    }

    /** Deletes a call argument with its name, colon and separating comma, leaving other arguments and comments intact. */
    public static void deleteArgument(PsiElement argument) {
        if (PhpArguments.name(argument) != null) {
            PsiElement colon = previous(argument), name = previous(colon);
            PsiElement start = name;
            argument.getParent().deleteChildRange(start, colon);
            PsiElement space = argument.getPrevSibling();
            if (space instanceof PsiWhiteSpace) space.delete();
        }
        PsiElement next = argument.getNextSibling();
        while (next instanceof PsiWhiteSpace) next = next.getNextSibling();
        if (next != null && ",".equals(next.getText())) {
            argument.getParent().deleteChildRange(argument, next);
            return;
        }
        PsiElement comma = previous(argument);
        if (comma != null && ",".equals(comma.getText())) comma.delete();
        if (argument.getPrevSibling() instanceof PsiWhiteSpace space) space.delete();
        argument.delete();
    }

    private static PsiElement previous(PsiElement element) {
        PsiElement sibling = element.getPrevSibling();
        while (sibling instanceof PsiWhiteSpace || sibling instanceof PsiComment) sibling = sibling.getPrevSibling();
        return sibling;
    }
}
