package io.github.byscrimm.yii2insight.views.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import io.github.byscrimm.yii2insight.common.PhpUtil;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import io.github.byscrimm.yii2insight.views.entities.ViewInfo;
import io.github.byscrimm.yii2insight.views.entities.ViewResolve;
import io.github.byscrimm.yii2insight.views.entities.ViewResolveFrom;
import io.github.byscrimm.yii2insight.views.index.ViewFiles;
import io.github.byscrimm.yii2insight.views.util.ViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ViewGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return new PsiElement[0];
        }

        Set<PsiElement> result = new HashSet<>();

        final ViewResolve resolve = ViewUtil.resolveView(psiElement);
        if (resolve != null) {
            Project project = psiElement.getProject();

            String key = resolve.key;
            if (FileUtilRt.getExtension(key).isEmpty()) {
                key = key + '.' + Yii2InsightSettings.getInstance(psiElement.getProject()).defaultViewExtension;
            }

            final @NotNull List<ViewInfo> views = ViewFiles.find(project, key, GlobalSearchScope.projectScope(project));
            if (!views.isEmpty()) {
                boolean localViewSearch = false;
                if (resolve.from == ViewResolveFrom.View) {
                    final String value = PhpUtil.getValue(psiElement);
                    localViewSearch = !value.startsWith("@") && !value.startsWith("//");
                }
                for (ViewInfo view : views) {
                    if (!resolve.application.equals(view.application)) {
                        continue;
                    }

                    if (localViewSearch && !resolve.theme.equals(view.theme)) {
                        continue;
                    }

                    if (view.getVirtualFile() == null) {
                        continue;
                    }

                    PsiFile file = PsiManager.getInstance(project).findFile(view.getVirtualFile());
                    result.add(file);
                }
            }
        }

        return result.toArray(new PsiElement[0]);
    }
}
