package io.github.byscrimm.yii2insight.i18n;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInspection.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.jetbrains.php.lang.inspections.PhpInspection;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.visitors.PhpElementVisitor;
import io.github.byscrimm.yii2insight.common.PhpArrays;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** A static {@code Yii::t()} message absent from every existing message file of its category. */
public final class MissingMessageInspection extends PhpInspection {
    @NotNull
    @Override
    public String getShortName() {
        return "Yii2InsightMissingMessageInspection";
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new PhpElementVisitor() {
            @Override
            public void visitPhpMethodReference(MethodReference reference) {
                if (DumbService.isDumb(reference.getProject())) return;
                TranslationCall call = TranslationCall.of(reference);
                if (call == null || call.categoryName() == null || call.messageText() == null) return;
                List<PsiFile> files = Util.getMessageFiles(reference, call.categoryName());
                if (files.isEmpty() || !Util.findMessage(reference, call.categoryName(), call.messageText()).isEmpty()) return;
                holder.registerProblem(call.message(), "Message is missing in category \"" + call.categoryName() + "\"",
                    ProblemHighlightType.WEAK_WARNING, new AddMessageFix(call.categoryName(), call.messageText()));
            }
        };
    }

    /** Appends {@code 'message' => ''} to each message file; an empty translation falls back to the source text. */
    static final class AddMessageFix implements LocalQuickFix {
        private final String category, message;
        AddMessageFix(String category, String message) { this.category = category; this.message = message; }

        @NotNull @Override public String getFamilyName() { return "Add message to category files"; }
        @NotNull @Override public String getName() { return "Add message to \"" + category + "\" files"; }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            List<PsiFile> files = Util.getMessageFiles(descriptor.getPsiElement(), category);
            if (!FileModificationService.getInstance().preparePsiElementsForWrite(files)) return;
            PsiDocumentManager documents = PsiDocumentManager.getInstance(project);
            for (PsiFile file : files) {
                ArrayCreationExpression array = PhpArrays.returnedArray(file);
                Document document = documents.getDocument(file);
                if (array == null || document == null || !Util.findMessage(descriptor.getPsiElement(), category, message).stream()
                        .noneMatch(found -> found.getContainingFile() == file)) continue;
                documents.doPostponedOperationsAndUnblockDocument(document);
                document.insertString(insertionOffset(array, document.getCharsSequence()), entry(array, document.getCharsSequence()));
                documents.commitDocument(document);
            }
        }

        private static int insertionOffset(ArrayCreationExpression array, CharSequence text) {
            ArrayHashElement last = null;
            for (ArrayHashElement element : array.getHashElements()) last = element;
            if (last == null) return array.getTextRange().getStartOffset() + (array.isShortSyntax() ? 1 : array.getText().indexOf('(') + 1);
            int offset = last.getTextRange().getEndOffset(), next = offset;
            while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
            return next < text.length() && text.charAt(next) == ',' ? next + 1 : offset;
        }

        private String entry(ArrayCreationExpression array, CharSequence text) {
            int offset = insertionOffset(array, text);
            boolean comma = array.getHashElements().iterator().hasNext() && text.charAt(offset - 1) != ',';
            String key = "'" + message.replace("\\", "\\\\").replace("'", "\\'") + "'";
            return (comma ? "," : "") + "\n    " + key + " => '',";
        }
    }
}
