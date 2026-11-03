package io.github.byscrimm.yii2insight.views.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by NVlad on 27.12.2016.
 */
class CompletionProvider extends com.intellij.codeInsight.completion.CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters completionParameters,
                                  @NotNull ProcessingContext processingContext,
                                  @NotNull CompletionResultSet completionResultSet) {

        final PsiElement psiElement = completionParameters.getPosition();
        final MethodReference method = PsiTreeUtil.getParentOfType(psiElement, MethodReference.class);

        if (method == null || method.getParameters().length == 0) {
            return;
        }

        if (!ViewUtil.isValidRenderMethod(method)) {
            return;
        }

        PsiElement viewParameter = psiElement;
        while (viewParameter != null && !(viewParameter.getParent() instanceof ParameterList)) {
            viewParameter = viewParameter.getParent();
        }

        if (viewParameter == null || !viewParameter.equals(method.getParameters()[0])) {
            return;
        }

        final ViewResolve resolve = ViewUtil.resolveView(viewParameter);
        if (resolve == null) {
            return;
        }

        final Project project = psiElement.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        int prefixLength = resolve.key.length();
        int lastSlashPosition = resolve.key.lastIndexOf('/');
        if (lastSlashPosition != -1 && !resolve.key.endsWith("/")) {
            prefixLength = lastSlashPosition + 1;
        }

        if (!completionParameters.isAutoPopup()) {
            if (completionResultSet.getPrefixMatcher().getPrefix().startsWith("@") && lastSlashPosition == -1) {
                final String prefix = completionResultSet.getPrefixMatcher().getPrefix();
                completionResultSet = completionResultSet.withPrefixMatcher(prefix.substring(1));
                prefixLength = 1;
            } else {
                completionResultSet = completionResultSet.withPrefixMatcher(resolve.key.substring(prefixLength));
            }

        }
        if (completionResultSet.getPrefixMatcher().getPrefix().equals("@")) {
            completionResultSet = completionResultSet.withPrefixMatcher("");
        }

        final String prefixFilter = resolve.key.substring(0, prefixLength);
        final Map<String, List<ViewInfo>> keys = ViewFiles.all(project, prefixFilter, scope);

        final PsiManager psiManager = PsiManager.getInstance(project);
        boolean localViewSearch = false;
        if (resolve.from == ViewResolveFrom.View) {
            final String value = PhpUtil.getValue(viewParameter);
            localViewSearch = !value.startsWith("@") && !value.startsWith("//");
        }

        final String defaultViewExtension = '.' + Yii2InsightSettings.getInstance(psiElement.getProject()).defaultViewExtension;
        for (Map.Entry<String, List<ViewInfo>> entry : keys.entrySet()) {
            final String key = entry.getKey();
            Collection<ViewInfo> views = entry.getValue();
            for (ViewInfo view : views) {
                if (!resolve.application.equals(view.application)) {
                    continue;
                }

                if (localViewSearch && !resolve.theme.equals(view.theme)) {
                    continue;
                }

                PsiFile psiFile = psiManager.findFile(view.getVirtualFile());
                if (psiFile != null) {
                    String insertText = key.substring(prefixLength);
                    if (insertText.endsWith(defaultViewExtension)) {
                        insertText = insertText.substring(0, insertText.length() - defaultViewExtension.length());
                    }
                    completionResultSet.addElement(new ViewLookupElement(psiFile, insertText));
                    break;
                } else {
                    System.out.println(view.fileUrl + " => not exists");
                }
            }
        }
    }
}
