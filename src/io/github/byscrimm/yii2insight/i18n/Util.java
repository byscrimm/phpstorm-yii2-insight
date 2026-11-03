package io.github.byscrimm.yii2insight.i18n;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.php.lang.psi.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by NVlad on 06.01.2017.
 */
class Util {
    /** Category name → its message file in the first locale that has it. */
    @NotNull
    static java.util.Map<String, PsiElement> getCategories(PhpPsiElement element) {
        java.util.Map<String, PsiElement> categories = new java.util.TreeMap<>();
        PsiManager manager = PsiManager.getInstance(element.getProject());
        TranslationSources.Config config = TranslationSources.of(element);
        for (TranslationSources.Source source : config.sources()) {
            for (VirtualFile locale : TranslationSources.locales(source.basePath(), config.language())) {
                for (java.util.Map.Entry<String, String> mapped : source.fileMap().entrySet()) {
                    VirtualFile file = locale.findFileByRelativePath(mapped.getValue());
                    PsiFile psi = file == null ? null : manager.findFile(file);
                    if (psi != null && TranslationSources.match(config.sources(), mapped.getKey()) == source) categories.putIfAbsent(mapped.getKey(), psi);
                }
                for (VirtualFile file : locale.getChildren()) {
                    if (file.isDirectory() || !"php".equals(file.getExtension()) || source.fileMap().containsValue(file.getName())) continue;
                    PsiFile psi = manager.findFile(file);
                    if (psi != null && TranslationSources.match(config.sources(), file.getNameWithoutExtension()) == source) categories.putIfAbsent(file.getNameWithoutExtension(), psi);
                }
            }
        }
        for (PsiDirectory directory : getDirectories(element)) {
            for (PsiFile file : directory.getFiles()) if (file.getName().endsWith(".php")) categories.putIfAbsent(file.getName().substring(0, file.getName().length() - 4), file);
        }
        return categories;
    }

    /** Message files of a category: configured PhpMessageSource first (application language first), else nearby messages directories. */
    @NotNull
    static java.util.List<PsiFile> getMessageFiles(PsiElement element, String category) {
        java.util.List<PsiFile> files = new java.util.ArrayList<>();
        PsiManager manager = PsiManager.getInstance(element.getProject());
        TranslationSources.Config config = TranslationSources.of(element);
        TranslationSources.Source source = TranslationSources.match(config.sources(), category);
        if (source != null) {
            for (VirtualFile locale : TranslationSources.locales(source.basePath(), config.language())) {
                VirtualFile file = locale.findFileByRelativePath(source.fileName(category));
                PsiFile psi = file == null ? null : manager.findFile(file);
                if (psi != null) files.add(psi);
            }
            return files;
        }
        for (PsiDirectory directory : getDirectories(element)) {
            PsiFile file = directory.findFile(category + ".php");
            if (file != null) files.add(file);
        }
        return files;
    }

    @NotNull
    static ArrayHashElement[] getMessages(PsiElement element, String category) {
        java.util.Map<String,ArrayHashElement> messages = new java.util.LinkedHashMap<>();
        for (PsiFile file : getMessageFiles(element, category)) {
            for (ArrayHashElement message : loadMessagesFromFile(file)) {
                String key = io.github.byscrimm.yii2insight.common.PhpArrays.string(message.getKey());
                if (key != null) messages.putIfAbsent(key, message);
            }
        }
        return messages.values().toArray(new ArrayHashElement[0]);
    }

    /** Message keys equal to {@code message} in every message file of the category. */
    @NotNull
    static java.util.List<StringLiteralExpression> findMessage(PsiElement element, String category, String message) {
        java.util.List<StringLiteralExpression> result = new java.util.ArrayList<>();
        for (PsiFile file : getMessageFiles(element, category)) {
            for (ArrayHashElement entry : loadMessagesFromFile(file)) {
                if (entry.getKey() instanceof StringLiteralExpression key && message.equals(key.getContents())) result.add(key);
            }
        }
        return result;
    }

    static java.util.Collection<ArrayHashElement> messages(PsiFile file) { return loadMessagesFromFile(file); }

    @NotNull
    static String PhpExpressionValue(PhpExpression expression) { return expressionValue(expression, 0); }
    private static String expressionValue(PhpExpression expression, int depth) {
        if (expression == null || depth > 32) return "";
        if (expression instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) expression).getContents();
        }
        if (expression instanceof ConstantReference) {
            Constant constant = (Constant) ((ConstantReference) expression).resolve();
            if (constant != null) {
                return expressionValue((PhpExpression) constant.getValue(), depth + 1);
            }
        }
        if (expression instanceof ClassConstantReference) {
            ClassReference classReference = (ClassReference) ((ClassConstantReference) expression).getClassReference();
            if (classReference != null) {
                PhpClass phpClass = (PhpClass) classReference.resolve();
                if (phpClass != null) {
                    Field field = phpClass.findFieldByName(expression.getName(), true);
                    if (field != null) {
                        return expressionValue((PhpExpression) field.getDefaultValue(), depth + 1);
                    }
                }
            }
        }
        if (expression instanceof Variable) {
            PhpExpression variable = (PhpExpression) ((Variable) expression).resolve();

            if (variable != null && variable.getContext() instanceof AssignmentExpression) {
                AssignmentExpression assignmentExpression = (AssignmentExpression) variable.getContext();
                return expressionValue((PhpExpression) assignmentExpression.getValue(), depth + 1);
            }
        }
        if (expression instanceof ConcatenationExpression) {
            ConcatenationExpression concatenation = (ConcatenationExpression) expression;
            return expressionValue((PhpExpression) concatenation.getLeftOperand(), depth + 1) + expressionValue((PhpExpression) concatenation.getRightOperand(), depth + 1);
        }
        String expressionType = expression.getType().toString();
        if (expressionType.equals("int") || expressionType.equals("float")) {
            return expression.getText();
        }

        return "";
    }

    private static java.util.List<PsiDirectory> getDirectories(PsiElement element) {
        java.util.List<PsiDirectory> result = new java.util.ArrayList<>();
        PsiFile file = element.getContainingFile();
        if (file == null) return result;
        String language = null;
        MethodReference call = com.intellij.psi.util.PsiTreeUtil.getParentOfType(element, MethodReference.class);
        if (call != null && call.getParameters().length > 3)
            language = io.github.byscrimm.yii2insight.common.PhpArrays.string(call.getParameters()[3]);
        PsiDirectory parent = file.getOriginalFile().getParent();
        while (parent != null) {
            PsiDirectory messages = parent.findSubdirectory("messages");
            if (messages != null) {
                if (language != null && messages.findSubdirectory(language) != null) result.add(messages.findSubdirectory(language));
                java.util.List<PsiDirectory> locales = new java.util.ArrayList<>(java.util.Arrays.asList(messages.getSubdirectories()));
                locales.sort(java.util.Comparator.comparing(PsiDirectory::getName));
                for (PsiDirectory locale : locales) if (!result.contains(locale)) result.add(locale);
            }
            if (parent.getVirtualFile().getPath().equals(element.getProject().getBasePath())) break;
            parent = parent.getParentDirectory();
        }
        return result;
    }
    private static Collection<ArrayHashElement> loadMessagesFromFile(PsiFile file) {
        ArrayCreationExpression array = io.github.byscrimm.yii2insight.common.PhpArrays.returnedArray(file);
        java.util.List<ArrayHashElement> result = new java.util.ArrayList<>();
        if (array != null) array.getHashElements().forEach(result::add);
        return result;
    }
}
