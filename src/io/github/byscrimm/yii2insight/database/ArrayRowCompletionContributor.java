package io.github.byscrimm.yii2insight.database;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbService;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression;
import com.jetbrains.php.lang.psi.elements.ArrayIndex;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import io.github.byscrimm.yii2insight.relations.ArrayRows;
import io.github.byscrimm.yii2insight.relations.YiiModelResolver;
import org.jetbrains.annotations.NotNull;

/** Keys of {@code asArray()} query rows in {@code $row['…']}. */
public final class ArrayRowCompletionContributor extends CompletionContributor {
    public ArrayRowCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(StringLiteralExpression.class), new CompletionProvider<>() {
            @Override protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext processing, @NotNull CompletionResultSet result) {
                if (DumbService.isDumb(parameters.getPosition().getProject())) return;
                StringLiteralExpression literal = (StringLiteralExpression) parameters.getPosition().getParent();
                if (!(literal.getParent() instanceof ArrayIndex index) || !(index.getParent() instanceof ArrayAccessExpression access)) return;
                ArrayRows.Row row = new ArrayRows(new YiiModelResolver(literal.getProject())).row(access.getValue());
                if (row == null) return;
                row.keys().forEach((key, nested) -> result.addElement(LookupElementBuilder.create(key).bold()
                    .withTypeText(nested == null ? "" : nested.collection() ? "array[]" : "array", true)));
            }
        });
    }
}
