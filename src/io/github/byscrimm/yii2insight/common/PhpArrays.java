package io.github.byscrimm.yii2insight.common;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import java.util.*;

/** Conservative static values: unknown PHP expressions remain unknown. */
public final class PhpArrays {
    private PhpArrays() {}
    public static String string(PsiElement element) {
        return element instanceof StringLiteralExpression literal ? literal.getContents() : null;
    }
    public static ArrayHashElement keyEntry(PsiElement element) {
        ArrayHashElement entry = PsiTreeUtil.getParentOfType(element, ArrayHashElement.class);
        return entry != null && entry.getKey() != null && PsiTreeUtil.isAncestor(entry.getKey(), element, false) ? entry : null;
    }
    public static PhpPsiElement value(ArrayCreationExpression array, String name) {
        for (ArrayHashElement item : array.getHashElements())
            if (name.equals(string(item.getKey()))) return item.getValue();
        return null;
    }
    public static String className(PsiElement value) {
        // 'app\\models\\User' and 'app\models\User' name the same class.
        if (value instanceof StringLiteralExpression literal) return literal.getContents().replace("\\\\", "\\");
        if (value instanceof ClassConstantReference ref && "class".equals(ref.getName()) && ref.getClassReference() instanceof ClassReference clazz)
            return clazz.getFQN();
        if (value instanceof MethodReference ref && "className".equals(ref.getName()) && ref.getClassReference() instanceof ClassReference clazz)
            return clazz.getFQN();
        if (value instanceof NewExpression expr && expr.getClassReference() != null) return expr.getClassReference().getFQN();
        if (value instanceof ArrayCreationExpression array) return className(value(array, "class"));
        return null;
    }
    public static ArrayCreationExpression returnedArray(PsiElement scope) {
        for (PhpReturn ret : PsiTreeUtil.findChildrenOfType(scope, PhpReturn.class)) {
            if (PsiTreeUtil.getParentOfType(ret, Function.class) != (scope instanceof Function ? scope : null)) continue;
            if (ret.getFirstPsiChild() instanceof ArrayCreationExpression array) return array;
        }
        return null;
    }
}
