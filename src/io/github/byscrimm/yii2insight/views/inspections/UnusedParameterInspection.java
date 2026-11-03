package io.github.byscrimm.yii2insight.views.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import io.github.byscrimm.yii2insight.common.YiiApplicationUtils;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import io.github.byscrimm.yii2insight.views.entities.ViewInfo;
import io.github.byscrimm.yii2insight.views.entities.ViewResolve;
import io.github.byscrimm.yii2insight.views.index.ViewFiles;
import io.github.byscrimm.yii2insight.views.util.RenderParameters;
import io.github.byscrimm.yii2insight.views.util.ViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Created by NVlad on 15.01.2017.
 */
final public class UnusedParameterInspection extends PhpInspection {
    @NotNull
    @Override
    public String getShortName() {
        return "Yii2InsightUnusedParameterInspection";
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder problemsHolder, boolean isOnTheFly) {
        return new PhpElementVisitor() {
            @Override
            public void visitPhpMethodReference(MethodReference reference) {
                if (!ViewUtil.isValidRenderMethod(reference)) {
                    return;
                }

                final String name = reference.getName();
                if (name == null || !ArrayUtil.contains(name, ViewUtil.renderMethods)) {
                    return;
                }

                final PsiElement view = RenderParameters.view(reference);
                final RenderParameters passed = RenderParameters.of(reference);
                // Only parameters written in this call are reported; a shared variable may serve other views.
                if (!(view instanceof StringLiteralExpression) || passed.argument() == null || !passed.complete() || !passed.direct()) {
                    return;
                }

                final ViewResolve resolve = ViewUtil.resolveView(view);
                if (resolve == null) {
                    return;
                }

                String key = resolve.key;
                if (FileUtilRt.getExtension(key).isEmpty()) {
                    key = key + '.' + Yii2InsightSettings.getInstance(reference.getProject()).defaultViewExtension;
                }

                final Project project = reference.getProject();
                final Collection<ViewInfo> views = ViewFiles.find(project, key, GlobalSearchScope.projectScope(project));
                final String application = YiiApplicationUtils.getApplicationName(reference.getContainingFile());
                views.removeIf(viewInfo -> !application.equals(viewInfo.application));
                if (views.size() == 0) {
                    return;
                }

                final Collection<String> viewParameters = new HashSet<>();
                for (ViewInfo info : views) {
                    viewParameters.addAll(info.parameters);
                }

                if (viewParameters.size() == 0) {
                    if (!passed.entries().isEmpty()) {
                        String errorUnusedParameters = "This View does not use parameters";
                        UnusedParametersLocalQuickFix fix = new UnusedParametersLocalQuickFix();
                        problemsHolder.registerProblem(passed.argument(), errorUnusedParameters, ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
                        if (isOnTheFly) {
                            problemsHolder.registerProblem(reference, errorUnusedParameters, ProblemHighlightType.INFORMATION, fix);
                        }
                    }
                    return;
                }

                final String errorUnusedParameter = "View " + view.getText() + " not use \"%parameter%\" parameter";
                final Set<String> unusedParameters = new HashSet<>();
                BiConsumer<String, PsiElement> processParameter = (String arrayKey, PsiElement element) -> {
                    if (!viewParameters.contains(arrayKey)) {
                        UnusedParameterLocalQuickFix fix = new UnusedParameterLocalQuickFix(arrayKey);
                        String description = errorUnusedParameter.replace("%parameter%", arrayKey);
                        problemsHolder.registerProblem(element, description, ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
                        unusedParameters.add(arrayKey);
                    }
                };

                for (RenderParameters.Entry entry : passed.entries()) {
                    processParameter.accept(entry.name(), entry.element());
                }

                if (unusedParameters.size() > 0 && isOnTheFly) {
                    if (viewParameters.containsAll(unusedParameters)) {
                        String errorUnusedParameters = "This View does not use parameters";
                        UnusedParametersLocalQuickFix fix = new UnusedParametersLocalQuickFix();
                        problemsHolder.registerProblem(passed.argument(), errorUnusedParameters, ProblemHighlightType.LIKE_UNUSED_SYMBOL, fix);
                        problemsHolder.registerProblem(reference, errorUnusedParameters, ProblemHighlightType.INFORMATION, fix);
                    } else {
                        String errorUnusedParameters = "This View have unused parameters";
                        UnusedParametersLocalQuickFix fix = new UnusedParametersLocalQuickFix(unusedParameters);
                        problemsHolder.registerProblem(reference, errorUnusedParameters, ProblemHighlightType.INFORMATION, fix);
                    }
                }
            }
        };
    }
}
