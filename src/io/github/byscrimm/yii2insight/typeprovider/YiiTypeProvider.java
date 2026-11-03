package io.github.byscrimm.yii2insight.typeprovider;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.VariableImpl;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import com.jetbrains.php.lang.psi.resolve.types.PhpTypeProvider4;
import io.github.byscrimm.yii2insight.common.ClassUtils;
import io.github.byscrimm.yii2insight.common.MethodUtils;
import io.github.byscrimm.yii2insight.common.PsiUtil;
import io.github.byscrimm.yii2insight.objectfactory.ObjectFactoryUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * Created by oleg on 2017-06-08.
 */
public class YiiTypeProvider implements PhpTypeProvider4 {
    final static char TRIM_KEY = '\u0197';
    final static char TRIM_KEY2 = '\u0199';
    final static char TRIM_KEY3 = '\u0198';

    @Override
    public char getKey() {
        return '\u0857';
    }

    @Nullable
    @Override
    public PhpType getType(PsiElement psiElement) {
        if (MethodUtils.isYiiCreateObjectMethod(psiElement)) {
            PhpPsiElement firstParam = (PhpPsiElement)MethodUtils.getParameter((MethodReference)psiElement, 0);
            //  Case: Yii::createObject($obj->array_var)
            if (firstParam instanceof FieldReference ) {
                String signature = ((MethodReference)psiElement).getSignature();
                return new PhpType().add("#" + this.getKey() + signature + TRIM_KEY + ((FieldReference) firstParam).getSignature());
            }
            // Case: Yii::createObject($array_var)
            if (firstParam instanceof Variable  && ((VariableImpl) firstParam).getType().getTypes().contains("\\array") ) {
                firstParam = getArrayCreationByVariableRef(firstParam);
            }
            if (firstParam instanceof ArrayCreationExpression) {
                return getClassByArrayCreationOptimized((ArrayCreationExpression) firstParam);
            }
            else {
                return withDefinition(getClass(firstParam), firstParam, psiElement);
            }
        } else if (psiElement instanceof MethodReference call && call.getParameters().length > 0
                && call.getClassReference() instanceof FieldReference owner && isYiiStatic(owner)) {
            // Yii::$app->get('cache') and Yii::$container->get(Cache::class)
            PhpPsiElement firstParam = (PhpPsiElement) call.getParameters()[0];
            if ("get".equals(call.getName()) && "app".equals(owner.getName()) && firstParam instanceof StringLiteralExpression name) {
                return componentType(name.getContents(), psiElement);
            }
            if ("get".equals(call.getName()) && "container".equals(owner.getName())) {
                return withDefinition(getClass(firstParam), firstParam, psiElement);
            }
        }else if(psiElement instanceof FieldReference) {
            String fieldName = PsiUtil.getYiiAppField((FieldReference) psiElement);
            if (fieldName != null) {
                return componentType(fieldName, psiElement);
            }
        }
        return null;
    }

    private static boolean isYiiStatic(FieldReference field) {
        return field.getClassReference() instanceof ClassReference yii && "Yii".equals(yii.getName());
    }

    @Nullable
    private PhpType componentType(String name, PsiElement element) {
        String origin = origin(element);
        return origin == null ? null : new PhpType().add("#" + this.getKey() + TRIM_KEY2 + name + TRIM_KEY2 + origin);
    }

    /** Adds the container definition of a statically named class; the declared class type is kept. */
    @Nullable
    private PhpType withDefinition(@Nullable PhpType declared, PsiElement name, PsiElement element) {
        String fqn = io.github.byscrimm.yii2insight.common.PhpArrays.className(name);
        String origin = origin(element);
        if (fqn == null || origin == null) return declared;
        PhpType type = new PhpType();
        if (declared != null) type.add(declared);
        return type.add("#" + this.getKey() + TRIM_KEY3 + (fqn.startsWith("\\") ? fqn : "\\" + fqn) + TRIM_KEY3 + origin);
    }

    @Nullable
    private static String origin(PsiElement element) {
        var file = element.getContainingFile() == null ? null : io.github.byscrimm.yii2insight.common.FileUtil.getVirtualFile(element.getContainingFile());
        return file == null ? null : java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(file.getUrl().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Override
    public @Nullable PhpType complete(String s, Project project) {
        if (com.intellij.openapi.project.DumbService.isDumb(project)) {
            return null;
        }
        PhpType phpType = new PhpType();

        int definitionStart = s.indexOf(TRIM_KEY3), definitionEnd = s.lastIndexOf(TRIM_KEY3);
        if (definitionStart > -1 && definitionStart < definitionEnd) {
            com.intellij.openapi.vfs.VirtualFile origin = originFile(s.substring(definitionEnd + 1));
            if (origin == null) return null;
            for (String className : io.github.byscrimm.yii2insight.configurations.ComponentResolver.definitions(project, origin, s.substring(definitionStart + 1, definitionEnd))) {
                for (PhpClass phpClass : PhpIndex.getInstance(project).getAnyByFQN(className)) {
                    phpType.add(phpClass.getType());
                }
            }
            return phpType;
        }

        int trimIndex = s.indexOf(TRIM_KEY);
        if (trimIndex > -1 && s.length() + 1 > trimIndex) {
            String origSignature = s.substring(0, trimIndex);
            if (origSignature.endsWith("\\Yii.createObject")) {
                String variableRef = s.substring(trimIndex + 1);
                final Collection<? extends PhpNamedElement> indexedVariabled = PhpIndex.getInstance(project).getBySignature(variableRef);
                for (PhpNamedElement elem : indexedVariabled) {
                    if (elem instanceof Field) {
                        Field field = (Field) elem;
                        if (field.getLastChild() instanceof ArrayCreationExpression) {
                            PhpClass classByArray = ObjectFactoryUtils.findClassByArray((ArrayCreationExpression) field.getLastChild());
                            if(classByArray != null) {
                                phpType.add(classByArray.getType());
                            }
                        }
                    }
                }
            }
        }else {
            int trimIndexStart = s.indexOf(TRIM_KEY2);
            int trimIndexEnd = s.lastIndexOf(TRIM_KEY2);
            if (trimIndexStart > -1 && trimIndexStart < trimIndexEnd) {
                String fieldName = s.substring(trimIndexStart + 1, trimIndexEnd);
                final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

                com.intellij.openapi.vfs.VirtualFile origin = originFile(s.substring(trimIndexEnd + 1));
                if (origin == null) return null;
                for (String className : io.github.byscrimm.yii2insight.configurations.ComponentResolver.classes(project, origin, fieldName)) {
                    for (PhpClass phpClass : PhpIndex.getInstance(project).getAnyByFQN(className)) {
                        phpType.add(phpClass.getType());
                    }
                }
            }
        }
        return phpType;
    }

    @Nullable
    private static com.intellij.openapi.vfs.VirtualFile originFile(String encoded) {
        try {
            String url = new String(java.util.Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
            return com.intellij.openapi.vfs.VirtualFileManager.getInstance().findFileByUrl(url);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @NotNull
    private PhpPsiElement getArrayCreationByVariableRef(PhpPsiElement firstParam) {
        Collection<? extends PhpNamedElement> localResolvedVariables = ((VariableImpl) firstParam).resolveLocal();
        if (localResolvedVariables.isEmpty()) return firstParam;
        PhpNamedElement firstElem = localResolvedVariables.iterator().next();
        if (firstElem instanceof Variable) {
            Variable variableDecl = (Variable)firstElem;
            if (variableDecl.getParent() != null) {
                PsiElement array = variableDecl.getParent().getLastChild();
                if (array instanceof ArrayCreationExpression) {
                    firstParam = (ArrayCreationExpression) array;
                }
            }
        }
        return firstParam;
    }

    @Nullable
    private PhpType getClassByArrayCreationOptimized(ArrayCreationExpression arrayCreationExpr) {
        for (ArrayHashElement elem : arrayCreationExpr.getHashElements()) {
            if (elem.getKey() != null && elem.getKey().getText() != null &&
                    ClassUtils.removeQuotes(elem.getKey().getText()).equals("class")) {
                return getClass(elem.getValue());
            }
        }
        return null;
    }

    private PhpType getClass(PhpPsiElement elem) {
        if (elem instanceof ClassConstantReference) {
            if (elem.getName() != null && elem.getName().equals("class")
                    && ((ClassConstantReference) elem).getClassReference() != null)
                return ((ClassConstantReference) elem).getClassReference().getType();
        }
        if (elem instanceof MethodReference) {
            if (elem.getName() != null && elem.getName().equals("className")
                    && ((MethodReference) elem).getClassReference() != null)
                return ((MethodReference) elem).getClassReference().getType();
        }

        return null;
    }

    @Override
    public Collection<? extends PhpNamedElement> getBySignature(String s, Set<String> set, int i, Project project) {
        return null;
    }

}
