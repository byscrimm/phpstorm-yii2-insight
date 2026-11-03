package io.github.byscrimm.yii2insight.views.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import io.github.byscrimm.yii2insight.common.PhpUtil;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import io.github.byscrimm.yii2insight.views.entities.ViewInfo;
import io.github.byscrimm.yii2insight.views.entities.ViewResolve;
import io.github.byscrimm.yii2insight.views.entities.ViewResolveFrom;
import io.github.byscrimm.yii2insight.views.index.ViewFiles;
import io.github.byscrimm.yii2insight.views.util.ViewUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by NVlad on 02.01.2017.
 */
class PsiReferenceProvider extends com.intellij.psi.PsiReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
        Set<PsiReference> references = new HashSet<>();

        final ViewResolve resolve = ViewUtil.resolveView(psiElement);
        if (resolve != null) {
            Project project = psiElement.getProject();

            String key = resolve.key;
            if (FileUtilRt.getExtension(key).isEmpty()) {
                key = key + '.' + Yii2InsightSettings.getInstance(psiElement.getProject()).defaultViewExtension;
            }

            final Collection<ViewInfo> views = ViewFiles.find(project, key, GlobalSearchScope.projectScope(project));

            if (views.size() > 0) {
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

                    PsiFile file = PsiManager.getInstance(project).findFile(view.getVirtualFile());
                    if (file != null) {
                        references.add(new PsiReference(psiElement, file));
                    }
                }
            }
        }

        return references.toArray(new PsiReference[0]);
    }
}
