package io.github.byscrimm.yii2insight.common;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.Function;
import java.util.*;

/**
 * External action ids of a controller's {@code actions()}: a returned array, or
 * {@code $actions = parent::actions()} adjusted by {@code unset($actions['id'])} and
 * {@code $actions['id'] = ...} (the yii\rest\ActiveController pattern). Unknown shapes yield nothing.
 */
public final class ControllerActions {
    @FunctionalInterface public interface Classes { Collection<PhpClass> get(String fqn); }
    private static final int MAX_DEPTH = 8;
    private ControllerActions() {}

    /** Action id → action class FQN; an id with an unresolvable class maps to null. */
    public static Map<String,String> of(PhpClass controller, Classes classes) { return of(controller, classes, 0); }

    private static Map<String,String> of(PhpClass controller, Classes classes, int depth) {
        if (controller == null || depth > MAX_DEPTH) return Map.of();
        ProgressManager.checkCanceled();
        Method actions = controller.findOwnMethodByName("actions");
        if (actions == null) {
            for (PhpClass parent : classes.get(controller.getSuperFQN())) return of(parent, classes, depth+1);
            return Map.of();
        }
        ArrayCreationExpression returned = PhpArrays.returnedArray(actions);
        if (returned != null) return entries(returned);
        // return $actions; where $actions starts from parent::actions()
        for (PhpReturn ret : PsiTreeUtil.findChildrenOfType(actions, PhpReturn.class)) {
            if (PsiTreeUtil.getParentOfType(ret, Function.class) != actions || !(ret.getFirstPsiChild() instanceof Variable variable)) continue;
            PsiElement start = LocalPhpValues.definition(variable.getName(), variable);
            Map<String,String> result;
            if (start instanceof MethodReference call && "actions".equals(call.getName()) && call.getClassReference() != null
                    && "parent".equals(call.getClassReference().getText())) {
                result = new LinkedHashMap<>();
                for (PhpClass parent : classes.get(controller.getSuperFQN())) { result.putAll(of(parent, classes, depth+1)); break; }
            } else if (start instanceof ArrayCreationExpression array) result = new LinkedHashMap<>(entries(array));
            else return Map.of();
            if (!adjust(actions, variable.getName(), start, ret, result)) return Map.of();
            return result;
        }
        return Map.of();
    }

    private static boolean adjust(Method actions, String name, PsiElement start, PhpReturn ret, Map<String,String> result) {
        int from = start.getTextRange().getEndOffset(), to = ret.getTextOffset();
        for (Variable use : PsiTreeUtil.findChildrenOfType(actions, Variable.class)) {
            int offset = use.getTextOffset();
            if (!name.equals(use.getName()) || offset <= from || offset >= to || PsiTreeUtil.getParentOfType(use, Function.class) != actions) continue;
            PsiElement parent = use.getParent();
            if (parent instanceof ArrayAccessExpression access && access.getIndex() != null
                    && PhpArrays.string(access.getIndex().getValue()) instanceof String id) {
                if (access.getParent() instanceof PhpUnset) { result.remove(id); continue; }
                if (access.getParent() instanceof AssignmentExpression assignment && assignment.getVariable() == access) {
                    result.put(id, PhpArrays.className(assignment.getValue()));
                    continue;
                }
                // $actions['index']['prepareDataProvider'] = ... keeps the action.
                if (access.getParent() instanceof ArrayAccessExpression) continue;
            }
            return false;
        }
        return true;
    }

    private static Map<String,String> entries(ArrayCreationExpression array) {
        Map<String,String> result = new LinkedHashMap<>();
        for (ArrayHashElement entry : array.getHashElements()) {
            String id = PhpArrays.string(entry.getKey());
            if (id != null) result.put(id, PhpArrays.className(entry.getValue()));
        }
        return result;
    }
}
