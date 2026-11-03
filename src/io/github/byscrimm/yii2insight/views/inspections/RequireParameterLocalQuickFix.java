package io.github.byscrimm.yii2insight.views.inspections;

import io.github.byscrimm.yii2insight.common.PhpArguments;
import io.github.byscrimm.yii2insight.views.util.RenderParameters;
import java.util.Arrays;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.jetbrains.php.lang.psi.PhpPsiElementFactory;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by NVlad on 15.01.2017.
 */
class RequireParameterLocalQuickFix implements LocalQuickFix {
    final private String[] myVariables;

    RequireParameterLocalQuickFix(String ...variables) {
        myVariables = variables;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
        if (myVariables.length == 1) {
            final String parameter = myVariables[0];
            return "Add \"%parameter%\" parameter".replace("%parameter%", parameter);
        }
        return "Add all missed View parameters";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
        return "Add View parameter";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final MethodReference call = (MethodReference) descriptor.getPsiElement();
        final ParameterList parameterList = call.getParameterList();
        if (parameterList == null) {
            return;
        }

        PsiElement params = RenderParameters.of(call).argument();
        if (params == null) {
            // Append the argument to the existing text so other arguments and comments stay as written.
            final boolean named = Arrays.stream(call.getParameters()).anyMatch(argument -> PhpArguments.name(argument) != null);
            final String text = parameterList.getText().stripTrailing();
            final String separator = text.isEmpty() || text.endsWith(",") ? "" : ", ";
            final ParameterList replacement = PhpPsiElementFactory.createFromText(project, ParameterList.class,
                    "f(" + text + separator + (named ? "params: []" : "[]") + ");");
            if (replacement == null) {
                return;
            }
            parameterList.replace(replacement);
            params = RenderParameters.of(call).argument();
            if (params == null) {
                return;
            }
        }

        TemplateManager templateManager = TemplateManager.getInstance(project);
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (editor == null) {
            return;
        }

        if (params instanceof FunctionReference) {
            FunctionReference functionReference = (FunctionReference) params;
            if (functionReference.getName() == null || !functionReference.getName().equals("compact")) {
                return;
            }

            Template template = templateManager.createTemplate("", "");
            template.setToReformat(true);

            Boolean firstElement = functionReference.getParameters().length == 0;
            for (String variable : myVariables) {
                template.addTextSegment(firstElement ? "'" : ", '");
                String var = "$" + variable.toUpperCase() + "$";
                template.addVariable(var, "", "\"" + variable + "\"", true);
                template.addVariableSegment(var);
                template.addTextSegment("'");
                firstElement = false;
            }

            PsiElement psiElement = functionReference.getParameterList();
            if (psiElement != null) {
                editor.getCaretModel().moveToOffset(psiElement.getTextRange().getEndOffset());
                PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
                templateManager.startTemplate(editor, template);
            }

            return;
        }

        if (!(params instanceof ArrayCreationExpression array)) {
            return;
        }

        Template template = templateManager.createTemplate("", "");
        template.setToReformat(true);
        Boolean addComma = array.getHashElements().iterator().hasNext();
        Boolean newLined = false;

        PsiElement psiElement = array.getLastChild();
        while (psiElement instanceof PsiWhiteSpace || psiElement.getText().equals("]")) {
            if (psiElement instanceof PsiWhiteSpace && psiElement.getText().contains("\n")) {
                newLined = true;
            }
            psiElement = psiElement.getPrevSibling();
        }
        if (psiElement.getText().equals(",")) {
            addComma = false;
        }

        for (String variable : myVariables) {
            if (addComma) {
                template.addTextSegment(",");
            }
            template.addTextSegment((newLined ? "\n" : " "));
            String templateVariable = "$" + variable.toUpperCase() + "$";
            template.addVariable(templateVariable, "", "\"$" + variable + "\"", true);
            template.addTextSegment("'" + variable + "' => ");
            template.addVariableSegment(templateVariable);
            addComma = true;
        }
        template.addTextSegment(newLined ? "," : " ");

        editor.getCaretModel().moveToOffset(psiElement.getTextRange().getEndOffset());

        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        templateManager.startTemplate(editor, template);
    }
}
