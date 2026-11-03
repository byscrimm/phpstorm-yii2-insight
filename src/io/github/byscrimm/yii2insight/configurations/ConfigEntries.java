package io.github.byscrimm.yii2insight.configurations;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.Function;
import io.github.byscrimm.yii2insight.common.PhpArrays;
import java.util.*;

/**
 * Content-only facts of one config file for {@link ComponentsIndex}. Static includes are recorded
 * as paths and followed at query time; nothing is executed and no other file is read here.
 */
public final class ConfigEntries {
    /** Component names in a {@code components} section. */
    public static final String COMPONENT = "";
    /** Root entries of a returned array; they are components only when a visible config includes the file. */
    public static final String ROOT = "@root:";
    /** Static paths included as the {@code components} section, separated by newlines. */
    public static final String INCLUDES = "@include:components";
    /** DI definitions: interface or class name → implementation class. */
    public static final String DEFINITION = "@di:";
    /** {@code controllerMap} id → controller class. */
    public static final String CONTROLLER = "@controller:";
    /** {@code i18n.translations} pattern → basePath, then {@code category=file} pairs, separated by {@link #SEPARATOR}. */
    public static final String TRANSLATION = "@i18n:";
    /** Application {@code language}. */
    public static final String LANGUAGE = "@language";
    public static final String SEPARATOR = "\u0001";
    private static final int MAX_DEPTH = 16;

    private ConfigEntries() {}

    /** Index entries of {@code file}; {@code directory} is the file's own directory for {@code __DIR__}. */
    public static Map<String,String> of(PsiFile file, String directory) {
        Map<String,String> result = new LinkedHashMap<>();
        Set<String> includes = new LinkedHashSet<>();
        for (ArrayHashElement hash : PsiTreeUtil.findChildrenOfType(file, ArrayHashElement.class)) {
            ProgressManager.checkCanceled();
            String key = PhpArrays.string(hash.getKey());
            if ("components".equals(key)) components(hash.getValue(), directory, result, includes, 0);
            else if ("translations".equals(key) && hash.getValue() instanceof ArrayCreationExpression translations) {
                ArrayHashElement owner = PsiTreeUtil.getParentOfType(hash, ArrayHashElement.class);
                if (owner == null || !"i18n".equals(PhpArrays.string(owner.getKey()))) continue;
                for (ArrayHashElement source : translations.getHashElements()) {
                    String pattern = PhpArrays.string(source.getKey());
                    if (pattern == null || !(source.getValue() instanceof ArrayCreationExpression config)) continue;
                    String className = PhpArrays.className(config);
                    // Only file-based PHP message sources are browsable.
                    if (className != null && !normalize(className).equals("\\yii\\i18n\\PhpMessageSource")) continue;
                    PsiElement base = PhpArrays.value(config, "basePath");
                    String basePath = base == null ? "@app/messages" : path(base, directory) != null ? path(base, directory) : PhpArrays.string(base);
                    if (basePath == null || basePath.contains(SEPARATOR)) continue;
                    StringBuilder value = new StringBuilder(basePath);
                    if (PhpArrays.value(config, "fileMap") instanceof ArrayCreationExpression fileMap)
                        for (ArrayHashElement mapped : fileMap.getHashElements()) {
                            String category = PhpArrays.string(mapped.getKey()), name = PhpArrays.string(mapped.getValue());
                            if (category != null && name != null && !category.contains("=")) value.append(SEPARATOR).append(category).append('=').append(name);
                        }
                    result.put(TRANSLATION + pattern, value.toString());
                }
            }
            else if ("language".equals(key) && hash.getParent() == PhpArrays.returnedArray(file) && PhpArrays.string(hash.getValue()) instanceof String language)
                result.put(LANGUAGE, language);
            else if ("controllerMap".equals(key) && hash.getValue() instanceof ArrayCreationExpression map) {
                for (ArrayHashElement entry : map.getHashElements()) {
                    String id = PhpArrays.string(entry.getKey()), className = component(entry.getValue());
                    if (id != null && className != null) result.put(CONTROLLER + id, normalize(className));
                }
            }
            else if ("container".equals(key) && hash.getValue() instanceof ArrayCreationExpression container) {
                for (String section : List.of("definitions", "singletons"))
                    if (PhpArrays.value(container, section) instanceof ArrayCreationExpression definitions) definitions(definitions, result);
            }
        }
        for (MethodReference call : PsiTreeUtil.findChildrenOfType(file, MethodReference.class)) {
            // Yii::$container->set(Interface::class, Implementation::class)
            if (!("set".equals(call.getName()) || "setSingleton".equals(call.getName()))
                    || !(call.getClassReference() instanceof FieldReference field) || !"container".equals(field.getName())
                    || !(field.getClassReference() instanceof ClassReference yii) || !"Yii".equals(yii.getName())) continue;
            PsiElement[] arguments = call.getParameters();
            String name = arguments.length > 0 ? PhpArrays.className(arguments[0]) : null;
            String implementation = arguments.length > 1 ? implementation(arguments[1]) : name;
            if (name != null && implementation != null) result.put(DEFINITION + normalize(name), normalize(implementation));
        }
        if (PhpArrays.returnedArray(file) instanceof ArrayCreationExpression root)
            for (ArrayHashElement entry : root.getHashElements()) {
                String name = PhpArrays.string(entry.getKey());
                String className = component(entry.getValue());
                if (name != null && className != null) result.putIfAbsent(ROOT + name, className);
            }
        if (!includes.isEmpty()) result.put(INCLUDES, String.join("\n", includes));
        return result;
    }

    private static void components(PsiElement value, String directory, Map<String,String> result, Set<String> includes, int depth) {
        if (value == null || depth > MAX_DEPTH) return;
        if (value instanceof ArrayCreationExpression components) {
            for (ArrayHashElement component : components.getHashElements()) {
                String name = PhpArrays.string(component.getKey());
                String className = component(component.getValue());
                if (name != null && className != null) result.put(name, className);
            }
        } else if (value instanceof Include include) {
            String path = path(include.getArgument(), directory);
            if (path != null) includes.add(path);
        } else if (value instanceof MethodReference merge && "merge".equals(merge.getName())
                && merge.getClassReference() instanceof ClassReference helper && "ArrayHelper".equals(helper.getName())) {
            // Later arguments override earlier ones; the indexer keeps the last value per name.
            for (PsiElement argument : merge.getParameters()) components(argument, directory, result, includes, depth+1);
        } else if (value instanceof ParenthesizedExpression parenthesized) components(parenthesized.getArgument(), directory, result, includes, depth+1);
    }

    private static void definitions(ArrayCreationExpression definitions, Map<String,String> result) {
        for (ArrayHashElement definition : definitions.getHashElements()) {
            String name = PhpArrays.className(definition.getKey());
            String implementation = implementation(definition.getValue());
            if (name != null && implementation != null) result.put(DEFINITION + normalize(name), normalize(implementation));
        }
    }

    private static String implementation(PsiElement value) {
        if (value instanceof ArrayCreationExpression array && PhpArrays.value(array, "class") == null) return null;
        return component(value);
    }

    /** Class of a component or definition: string, ::class, ['class' => ...] or a closure returning new/::class. */
    static String component(PsiElement value) {
        String className = PhpArrays.className(value);
        // A closure value can be a PhpExpression wrapper around the Function.
        Function factory = value instanceof Function f ? f
            : value instanceof PhpExpression && value.getFirstChild() instanceof Function f ? f : null;
        if (className == null && factory != null) {
            for (PhpReturn ret : PsiTreeUtil.findChildrenOfType(factory, PhpReturn.class)) {
                if (PsiTreeUtil.getParentOfType(ret, Function.class) != factory) continue;
                className = PhpArrays.className(ret.getFirstPsiChild());
                if (className != null) break;
            }
        }
        return className == null || className.isBlank() || !className.matches("\\\\?[A-Za-z_][\\w\\\\]*") ? null : className;
    }

    private static String normalize(String name) { return name.startsWith("\\") ? name : "\\" + name; }

    /** {@code __DIR__ . '/x.php'}, {@code dirname(__DIR__) . '/config/x.php'} and absolute literals; otherwise null. */
    static String path(PsiElement expression, String directory) {
        String path = evaluate(expression, directory, 0);
        if (path == null || !path.startsWith("/")) return null;
        Deque<String> parts = new ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (parts.isEmpty()) return null; parts.removeLast(); }
            else parts.addLast(part);
        }
        return "/" + String.join("/", parts);
    }

    private static String evaluate(PsiElement expression, String directory, int depth) {
        if (expression == null || depth > MAX_DEPTH) return null;
        if (expression instanceof ParenthesizedExpression parenthesized) return evaluate(parenthesized.getArgument(), directory, depth+1);
        if (expression instanceof StringLiteralExpression literal) return literal.getContents().contains("$") ? null : literal.getContents();
        if (expression instanceof ConstantReference constant && "__DIR__".equals(constant.getName())) return directory;
        if (expression instanceof FunctionReference function && !(expression instanceof MethodReference) && "dirname".equals(function.getName())
                && function.getParameters().length >= 1) {
            String inner = evaluate(function.getParameters()[0], directory, depth+1);
            int levels = 1;
            if (function.getParameters().length == 2) {
                try { levels = Integer.parseInt(function.getParameters()[1].getText()); } catch (NumberFormatException e) { return null; }
            }
            for (int i = 0; i < levels && inner != null; i++) {
                int slash = inner.lastIndexOf('/');
                inner = slash <= 0 ? null : inner.substring(0, slash);
            }
            return inner;
        }
        if (expression instanceof BinaryExpression binary && binary.getOperation() != null && ".".equals(binary.getOperation().getText())) {
            String left = evaluate(binary.getLeftOperand(), directory, depth+1), right = evaluate(binary.getRightOperand(), directory, depth+1);
            return left == null || right == null ? null : left + right;
        }
        return null;
    }
}
