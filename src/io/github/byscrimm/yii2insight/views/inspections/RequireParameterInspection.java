package io.github.byscrimm.yii2insight.views.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import io.github.byscrimm.yii2insight.common.PhpUtil;
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
import java.util.Iterator;

/**
 * Created by NVlad on 23.01.2017.
 */
public class RequireParameterInspection extends PhpInspection {
    @NotNull
    @Override
    public String getShortName() {
        return "Yii2InsightRequireParameterInspection";
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
                if (!(view instanceof StringLiteralExpression)) {
                    return;
                }
                final RenderParameters passed = RenderParameters.of(reference);
                if (!passed.complete()) {
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
                    return;
                }


                final Collection<String> existKeys = passed.names();
                viewParameters.removeIf(existKeys::contains);
                if (viewParameters.size() == 0) {
                    return;
                }

                String description = "View " + view.getText() + " require ";
                final Iterator<String> parameterIterator = viewParameters.iterator();
                if (!isOnTheFly) {
                    while (parameterIterator.hasNext()) {
                        final String parameter = parameterIterator.next();
                        final String problemDescription = description + "\"" + parameter + "\" parameter.";
                        problemsHolder.registerProblem(reference, problemDescription, passed.direct() ? new LocalQuickFix[]{new RequireParameterLocalQuickFix(parameter)} : LocalQuickFix.EMPTY_ARRAY);
                    }
                    return;
                }

                final Collection<LocalQuickFix> fixes = new HashSet<>();
                if (viewParameters.size() > 1) {
                    fixes.add(new RequireParameterLocalQuickFix(viewParameters.toArray(new String[0])));
                    StringBuilder parameterString = new StringBuilder();
                    String parameter = parameterIterator.next();
                    while (parameterIterator.hasNext()) {
                        if (parameterString.length() > 0) {
                            parameterString.append(", ");
                        }
                        parameterString.append("\"").append(parameter).append("\"");
                        fixes.add(new RequireParameterLocalQuickFix(parameter));
                        parameter = parameterIterator.next();
                    }
                    parameterString.append(" and \"").append(parameter).append("\" parameters.");
                    description += parameterString.toString();
                    fixes.add(new RequireParameterLocalQuickFix(parameter));
                } else {
                    String parameter = parameterIterator.next();
                    description += "\"" + parameter + "\" parameter.";
                    fixes.add(new RequireParameterLocalQuickFix(parameter));
                }
                problemsHolder.registerProblem(reference, description, passed.direct() ? fixes.toArray(new LocalQuickFix[0]) : LocalQuickFix.EMPTY_ARRAY);
            }
        };
    }
}
