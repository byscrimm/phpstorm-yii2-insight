package io.github.byscrimm.yii2insight.common;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.*;
import java.util.*;

public final class UrlUtils {
    /** {@code config} is the declaring config file of a controllerMap route, otherwise null. */
    private record Route(String name, Method action, PhpClass controller, VirtualFile config) {}
    private static List<Route> routes(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
            List<Route> result = new ArrayList<>();
            for (PhpClass controller : PhpIndex.getInstance(project).getAllSubclasses("\\yii\\web\\Controller")) {
                ProgressManager.checkCanceled();
                if (controller.isAbstract()) continue;
                String prefix = controllerRoute(controller);
                if (prefix == null) continue;
                actions(project, controller, prefix, null, result);
            }
            // controllerMap ids replace the path-derived controller id; visibility follows the declaring config.
            com.intellij.util.indexing.FileBasedIndex index = com.intellij.util.indexing.FileBasedIndex.getInstance();
            var scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project);
            index.processAllKeys(io.github.byscrimm.yii2insight.configurations.ComponentsIndex.identity, key -> {
                if (!key.startsWith(io.github.byscrimm.yii2insight.configurations.ConfigEntries.CONTROLLER)) return true;
                String id = key.substring(io.github.byscrimm.yii2insight.configurations.ConfigEntries.CONTROLLER.length());
                index.processValues(io.github.byscrimm.yii2insight.configurations.ComponentsIndex.identity, key, null, (file, fqn) -> {
                    for (PhpClass controller : PhpIndex.getInstance(project).getAnyByFQN(fqn)) actions(project, controller, id, file, result);
                    return true;
                }, scope);
                return true;
            }, scope, null);
            return CachedValueProvider.Result.create(List.copyOf(result), PsiModificationTracker.MODIFICATION_COUNT);
        });
    }
    private static void actions(Project project, PhpClass controller, String prefix, VirtualFile config, List<Route> result) {
        for (Method method : controller.getMethods()) {
            String name = method.getName();
            if (method.getAccess().isPublic() && !method.isStatic() && name.length() > 6
                    && name.startsWith("action") && Character.isUpperCase(name.charAt(6))) {
                result.add(new Route(prefix + "/" + RouteNames.id(name.substring(6)), method, controller, config));
            }
        }
        PhpIndex index = PhpIndex.getInstance(project);
        for (Map.Entry<String,String> action : ControllerActions.of(controller, index::getAnyByFQN).entrySet()) {
            PhpClass type = action.getValue() == null ? null : ClassUtils.getClass(index, action.getValue());
            Method run = type == null ? null : type.findMethodByName("run");
            if (run != null) result.add(new Route(prefix + "/" + action.getKey(), run, controller, config));
        }
    }
    public static String controllerRoute(PhpClass controller) {
        if (!controller.getName().endsWith("Controller") || controller.getContainingFile() == null) return null;
        VirtualFile file = FileUtil.getVirtualFile(controller.getContainingFile());
        VirtualFile root = YiiContext.applicationRoot(controller);
        if (file == null || root == null || !file.getPath().startsWith(root.getPath() + "/")) return null;
        String path = file.getPath().substring(root.getPath().length() + 1);
        if (path.startsWith("vendor/")) return null;
        int idx = path.lastIndexOf("controllers/");
        if (idx < 0) return null;
        String modules = RouteNames.modulePrefix(path);
        String subdir = path.substring(idx + 12, path.lastIndexOf('/') + 1);
        String name = controller.getName();
        return RouteNames.normalize(modules + subdir + RouteNames.id(name.substring(0, name.length() - 10)));
    }
    public static HashMap<String, Method> getRoutes(PsiElement origin) {
        HashMap<String, Method> result = new LinkedHashMap<>();
        for (Route route : routes(origin.getProject())) {
            boolean visible = route.config == null ? YiiContext.sameApplication(origin, route.controller)
                    : origin.getContainingFile() != null && YiiContext.configPriority(FileUtil.getVirtualFile(origin.getContainingFile()), route.config, origin.getProject().getBasePath()) >= 0;
            if (visible) result.put(route.name, route.action);
        }
        return result;
    }
    public static String resolveRoute(String value, PsiElement origin) {
        PhpClass controller = com.intellij.psi.util.PsiTreeUtil.getParentOfType(origin, PhpClass.class);
        String prefix = controller == null ? viewController(origin) : controllerRoute(controller);
        VirtualFile root = YiiContext.applicationRoot(origin);
        VirtualFile file = origin.getContainingFile() == null ? null : FileUtil.getVirtualFile(origin.getContainingFile());
        String module = root != null && file != null && file.getPath().startsWith(root.getPath() + "/")
                ? RouteNames.modulePrefix(file.getPath().substring(root.getPath().length() + 1)) : "";
        return RouteNames.resolve(value, prefix, module);
    }
    private static String viewController(PsiElement origin) {
        VirtualFile root = YiiContext.applicationRoot(origin);
        VirtualFile file = FileUtil.getVirtualFile(origin.getContainingFile());
        if (root == null || file == null || !file.getPath().startsWith(root.getPath() + "/")) return null;
        String path = file.getPath().substring(Math.min(file.getPath().length(), root.getPath().length() + 1));
        int idx = path.lastIndexOf("views/");
        if (idx < 0 || path.lastIndexOf('/') <= idx + 5) return null;
        return RouteNames.normalize(RouteNames.modulePrefix(path) + path.substring(idx + 6, path.lastIndexOf('/')));
    }
    public static Method resolve(String route, PsiElement origin) {
        return getRoutes(origin).get(resolveRoute(route, origin));
    }
    public static Parameter[] getParamsByUrl(String route, PsiElement origin) {
        Method method = resolve(route, origin);
        return method == null ? null : method.getParameters();
    }
}
