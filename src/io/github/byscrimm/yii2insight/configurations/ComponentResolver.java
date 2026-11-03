package io.github.byscrimm.yii2insight.configurations;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import io.github.byscrimm.yii2insight.common.FileUtil;
import io.github.byscrimm.yii2insight.common.YiiContext;
import com.intellij.openapi.progress.ProgressManager;
import java.util.*;

public final class ComponentResolver {
    private ComponentResolver() {}

    /**
     * Component classes visible from {@code origin}: its application config, local overrides and the
     * shared common config, including static {@code 'components' => require ...} files.
     */
    public static Set<String> classes(Project project, VirtualFile origin, String name) {
        if (origin == null || name.startsWith("@")) return Set.of();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        FileBasedIndex index = FileBasedIndex.getInstance();
        Map<String,Integer> included = new HashMap<>();
        index.processValues(ComponentsIndex.identity, ConfigEntries.INCLUDES, null, (file, paths) -> {
            int priority = YiiContext.configPriority(origin, file, project.getBasePath());
            if (priority >= 0) for (String path : paths.split("\n")) included.merge(path, priority, Math::max);
            return true;
        }, scope);
        Set<String> values = new LinkedHashSet<>();
        int[] best = {-1};
        index.processValues(ComponentsIndex.identity, name, null, (file, value) -> {
            best(values, best, YiiContext.configPriority(origin, file, project.getBasePath()), value);
            return true;
        }, scope);
        if (!included.isEmpty()) index.processValues(ComponentsIndex.identity, ConfigEntries.ROOT + name, null, (file, value) -> {
            Integer priority = included.get(file.getPath());
            if (priority != null) best(values, best, priority, value);
            return true;
        }, scope);
        return values;
    }

    /** DI implementations of {@code fqn} visible from {@code origin}. */
    public static Set<String> definitions(Project project, VirtualFile origin, String fqn) {
        if (origin == null) return Set.of();
        Set<String> values = new LinkedHashSet<>();
        int[] best = {-1};
        FileBasedIndex.getInstance().processValues(ComponentsIndex.identity, ConfigEntries.DEFINITION + fqn, null, (file, value) -> {
            best(values, best, YiiContext.configPriority(origin, file, project.getBasePath()), value);
            return true;
        }, GlobalSearchScope.projectScope(project));
        return values;
    }

    private static void best(Set<String> values, int[] best, int priority, String value) {
        ProgressManager.checkCanceled();
        if (priority < 0 || priority < best[0]) return;
        if (priority > best[0]) { values.clear(); best[0] = priority; }
        values.add(value);
    }
    public static Set<String> classes(PsiElement origin, String name) {
        return classes(origin.getProject(), FileUtil.getVirtualFile(origin.getContainingFile()), name);
    }
}
