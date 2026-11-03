package io.github.byscrimm.yii2insight.i18n;

import com.intellij.openapi.project.DumbService;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/** Ctrl+Click on a {@code Yii::t()} message opens its entries in every locale's message file. */
public final class MessageReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(StringLiteralExpression.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
                StringLiteralExpression literal = (StringLiteralExpression) element;
                TranslationCall call = TranslationCall.ofMessage(literal);
                if (call == null || call.categoryName() == null || call.messageText() == null) return PsiReference.EMPTY_ARRAY;
                return new PsiReference[]{new MessageReference(literal, call.categoryName(), call.messageText())};
            }
        });
    }

    private static final class MessageReference extends PsiPolyVariantReferenceBase<StringLiteralExpression> {
        private final String category, message;
        MessageReference(StringLiteralExpression element, String category, String message) {
            super(element, true);
            this.category = category;
            this.message = message;
        }
        @Override
        public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
            if (DumbService.isDumb(getElement().getProject())) return ResolveResult.EMPTY_ARRAY;
            return PsiElementResolveResult.createResults(Util.findMessage(getElement(), category, message));
        }
        @Override
        public Object @NotNull [] getVariants() { return EMPTY_ARRAY; }
    }
}
