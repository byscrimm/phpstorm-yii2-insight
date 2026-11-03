package io.github.byscrimm.yii2insight.views.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import io.github.byscrimm.yii2insight.common.PhpUtil;
import io.github.byscrimm.yii2insight.common.YiiApplicationUtils;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import io.github.byscrimm.yii2insight.views.entities.ViewInfo;
import io.github.byscrimm.yii2insight.views.entities.ViewResolve;
import io.github.byscrimm.yii2insight.views.entities.ViewResolveFrom;
import io.github.byscrimm.yii2insight.views.index.ViewFiles;
import io.github.byscrimm.yii2insight.views.util.RenderParameters;
import io.github.byscrimm.yii2insight.views.util.RenderUtil;
import io.github.byscrimm.yii2insight.views.util.ViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Created by NVlad on 15.01.2017.
 */
final public class MissedViewInspection extends PhpInspection {
    @NotNull
    @Override
    public String getShortName() {
        return "Yii2InsightMissedViewInspection";
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

                if (ArrayUtil.contains(reference.getName(), ViewUtil.renderMethods)) {
                    if (RenderParameters.view(reference) != null) {
                        final PsiElement pathParameter = RenderParameters.view(reference);
                        final ViewResolve resolve = ViewUtil.resolveView(pathParameter);
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
                        final boolean localViewSearch;
                        final String value = PhpUtil.getValue(pathParameter);
                        if (resolve.from == ViewResolveFrom.View) {
                            localViewSearch = !value.startsWith("@") && !value.startsWith("//");
                        } else {
                            localViewSearch = false;
                        }

                        views.removeIf(view -> {
                            if (!application.equals(view.application)) {
                                return true;
                            }

                            return localViewSearch && !resolve.theme.equals(view.theme);
                        });
                        if (views.size() != 0) {
                            return;
                        }

                        if (pathParameter instanceof StringLiteralExpression) {
                            Collection<String> paths = ViewUtil.viewResolveToPaths(resolve, project);
                            if (!paths.iterator().hasNext()) {
                                return;
                            }

                            VirtualFile yiiRoot = YiiApplicationUtils.getYiiRootVirtualFile(project);
                            if (yiiRoot == null) {
                                return;
                            }

                            int projectUrlLength = project.getBaseDir().getUrl().length();
                            String yiiRootUrl = yiiRoot.getUrl();
                            String path;
                            if (projectUrlLength > yiiRootUrl.length()) {
                                path = paths.iterator().next();
                            } else {
                                path = yiiRootUrl.substring(projectUrlLength) + paths.iterator().next();
                            }
                            final String viewNotFoundMessage = "View file for \"" + value + "\" not found in \"" + path + "\".";
                            final MissedViewLocalQuickFix quickFix = new MissedViewLocalQuickFix(value, path, RenderUtil.getViewArguments(reference));
                            final PsiElement stringPart = pathParameter.findElementAt(1);
                            if (stringPart != null) {
                                problemsHolder.registerProblem(stringPart, viewNotFoundMessage, quickFix);
                            }
                        }
                    }
                }
            }
        };
    }
}
