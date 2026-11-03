package io.github.byscrimm.yii2insight.i18n;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Created by NVlad on 06.01.2017.
 */
class CategoryLookupElement extends LookupElement {
    final private PsiElement myCategory;
    final private String myName;

    CategoryLookupElement(PsiElement category, String name) {
        myCategory = category;
        myName = name;
    }

    @NotNull
    @Override
    public String getLookupString() {
        return myName;
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
        super.renderElement(presentation);

        if (myCategory instanceof PsiFile) {
            PsiFile file = (PsiFile) myCategory;
            String filename = file.getName();
            presentation.setIcon(file.getIcon(0));
            presentation.setItemText(myName);
            if (!filename.equals(myName + ".php")) presentation.setTailText("  " + filename, true);
        }
    }
}
