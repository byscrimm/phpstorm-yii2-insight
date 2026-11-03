package io.github.byscrimm.yii2insight.configurations;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.psi.elements.*;
import io.github.byscrimm.yii2insight.common.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class YiiAppCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    protected void addCompletions(@NotNull CompletionParameters completionParameters, @NotNull ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
        final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        final PsiElement psiElement = completionParameters.getPosition();
        final Project project = psiElement.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        if(psiElement.getParent() instanceof FieldReference){
            String field = PsiUtil.getYiiAppField((FieldReference) psiElement.getParent());
            if(field != null){
                final java.util.Set<String> added = new java.util.HashSet<>();
                fileBasedIndex.processAllKeys(ComponentsIndex.identity, indexKey -> {
                    // Components of included files are stored under the ROOT prefix; other @ keys are not components.
                    final String key = indexKey.startsWith(ConfigEntries.ROOT) ? indexKey.substring(ConfigEntries.ROOT.length()) : indexKey;
                    if (key.startsWith("@") || !added.add(key)) return true;
                    java.util.List<String> values = new java.util.ArrayList<>(ComponentResolver.classes(psiElement, key));
                    if (values.isEmpty()) added.remove(key);
                    if(values.size() > 0){
                        LookupElementBuilder lookupElement = LookupElementBuilder.create(key)
                                .withTypeText(values.get(0), true)
                                .withIcon(AllIcons.Nodes.Class);

                        completionResultSet.addElement(lookupElement);
                    }
                    return true;
                }, project);
            }
        }
    }
}
