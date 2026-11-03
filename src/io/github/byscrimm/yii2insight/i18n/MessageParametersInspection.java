package io.github.byscrimm.yii2insight.i18n;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import io.github.byscrimm.yii2insight.common.IcuPlaceholders;
import io.github.byscrimm.yii2insight.common.PhpArrays;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/** ICU placeholders of a static {@code Yii::t()} message versus a literal params array. */
public final class MessageParametersInspection extends PhpInspection {
    @NotNull
    @Override
    public String getShortName() {
        return "Yii2InsightMessageParametersInspection";
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PhpElementVisitor() {
            @Override
            public void visitPhpMethodReference(MethodReference reference) {
                TranslationCall call = TranslationCall.of(reference);
                if (call == null || call.message() == null) return;
                Problems problems = problems(call);
                if (problems == null) return;
                for (String name : problems.missing) {
                    holder.registerProblem(call.params() != null ? call.params() : call.message(),
                        "Message placeholder {" + name + "} has no parameter", ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                for (Map.Entry<String, PsiElement> unused : problems.unused.entrySet()) {
                    holder.registerProblem(unused.getValue(), "Parameter \"" + unused.getKey() + "\" is not used by the message", ProblemHighlightType.LIKE_UNUSED_SYMBOL);
                }
            }
        };
    }

    public record Problems(Set<String> missing, Map<String, PsiElement> unused) {}

    /** Null when the message or the parameters are not statically known. */
    public static Problems problems(TranslationCall call) {
        Set<String> placeholders = IcuPlaceholders.of(call.messageText());
        if (placeholders == null) return null;
        Map<String, PsiElement> parameters = new LinkedHashMap<>();
        if (call.params() instanceof ArrayCreationExpression array) {
            int position = 0;
            for (PsiElement child : array.getChildren()) {
                if (child instanceof ArrayHashElement hash) {
                    String key = PhpArrays.string(hash.getKey());
                    if (key == null && hash.getKey() != null && hash.getKey().getText().matches("\\d+")) key = hash.getKey().getText();
                    if (key == null) return null;
                    parameters.put(key, hash);
                } else if (child instanceof PhpPsiElement value) {
                    if (value.getText().startsWith("...")) return null;
                    parameters.put(String.valueOf(position++), value);
                }
            }
        } else if (call.params() != null) {
            return null;
        }
        // Yii formats only non-empty params; a message without parameters is returned unchanged.
        Set<String> missing = new LinkedHashSet<>(placeholders);
        missing.removeAll(parameters.keySet());
        parameters.keySet().removeAll(placeholders);
        return new Problems(missing, parameters);
    }
}
