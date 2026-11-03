package io.github.byscrimm.yii2insight.i18n;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import io.github.byscrimm.yii2insight.common.PhpArguments;
import org.jetbrains.annotations.Nullable;

/** {@code Yii::t($category, $message, $params, $language)} with positional or named arguments. */
public record TranslationCall(MethodReference call, @Nullable PsiElement category, @Nullable PsiElement message, @Nullable PsiElement params) {
    @Nullable
    public static TranslationCall of(PsiElement element) {
        if (!(element instanceof MethodReference call) || !"t".equals(call.getName())
                || !(call.getClassReference() instanceof ClassReference yii) || !"Yii".equals(yii.getName())) return null;
        return new TranslationCall(call, PhpArguments.get(call, "category", 0), PhpArguments.get(call, "message", 1), PhpArguments.get(call, "params", 2));
    }

    /** The call whose message argument is {@code literal}. */
    @Nullable
    static TranslationCall ofMessage(StringLiteralExpression literal) {
        TranslationCall call = of(PsiTreeUtil.getParentOfType(literal, MethodReference.class));
        return call != null && call.message == literal ? call : null;
    }

    /** Static category and message text; null for interpolated or dynamic values. */
    @Nullable String categoryName() { return text(category); }
    @Nullable String messageText() { return text(message); }

    private static String text(PsiElement element) {
        if (!(element instanceof StringLiteralExpression literal)) return null;
        // Double-quoted strings with variables are not static messages.
        return !literal.isSingleQuote() && literal.getText().contains("$") ? null : literal.getContents();
    }
}
