package io.github.byscrimm.yii2insight.i18n;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import io.github.byscrimm.yii2insight.common.FileUtil;
import io.github.byscrimm.yii2insight.common.YiiContext;
import io.github.byscrimm.yii2insight.configurations.ComponentsIndex;
import io.github.byscrimm.yii2insight.configurations.ConfigEntries;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/**
 * {@code i18n.translations} PhpMessageSource configuration visible from a file: base path,
 * fileMap and application language, read from the components index at query time.
 */
public final class TranslationSources {
    /** A configured source; {@code basePath} is an absolute directory. */
    public record Source(String pattern, VirtualFile basePath, Map<String,String> fileMap) {
        String fileName(String category) {
            String mapped = fileMap.get(category);
            return mapped != null ? mapped : category.replace('\\', '/') + ".php";
        }
    }
    record Config(List<Source> sources, @Nullable String language) {}

    private TranslationSources() {}

    static Config of(PsiElement origin) {
        Project project = origin.getProject();
        VirtualFile file = origin.getContainingFile() == null ? null : FileUtil.getVirtualFile(origin.getContainingFile().getOriginalFile());
        if (file == null) return new Config(List.of(), null);
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        FileBasedIndex index = FileBasedIndex.getInstance();
        Map<String,Source> sources = new LinkedHashMap<>();
        Map<String,Integer> priorities = new HashMap<>();
        index.processAllKeys(ComponentsIndex.identity, key -> {
            if (!key.startsWith(ConfigEntries.TRANSLATION)) return true;
            String pattern = key.substring(ConfigEntries.TRANSLATION.length());
            index.processValues(ComponentsIndex.identity, key, null, (config, value) -> {
                ProgressManager.checkCanceled();
                int priority = YiiContext.configPriority(file, config, project.getBasePath());
                if (priority < 0 || priority < priorities.getOrDefault(pattern, -1)) return true;
                String[] parts = value.split(ConfigEntries.SEPARATOR);
                VirtualFile base = basePath(parts[0], config, project);
                if (base == null) return true;
                Map<String,String> fileMap = new LinkedHashMap<>();
                for (int i = 1; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    if (eq > 0) fileMap.put(parts[i].substring(0, eq), parts[i].substring(eq + 1));
                }
                priorities.put(pattern, priority);
                sources.put(pattern, new Source(pattern, base, fileMap));
                return true;
            }, scope);
            return true;
        }, scope, null);
        String[] language = {null};
        int[] best = {-1};
        index.processValues(ComponentsIndex.identity, ConfigEntries.LANGUAGE, null, (config, value) -> {
            int priority = YiiContext.configPriority(file, config, project.getBasePath());
            if (priority > best[0]) { best[0] = priority; language[0] = value; }
            return true;
        }, scope);
        return new Config(List.copyOf(sources.values()), language[0]);
    }

    /** Yii I18N order: exact category, then a matching {@code prefix*} pattern (longest here), then {@code *}. */
    @Nullable
    public static Source match(List<Source> sources, String category) {
        Source best = null;
        int length = -1;
        for (Source source : sources) {
            String pattern = source.pattern;
            if (pattern.equals(category)) return source;
            if (pattern.endsWith("*") && category.startsWith(pattern.substring(0, pattern.length() - 1)) && pattern.length() > length) {
                best = source;
                length = pattern.length();
            }
        }
        return best;
    }

    /** Absolute paths and {@code @app} / {@code @common} relative to the declaring application; other aliases are unknown. */
    @Nullable
    private static VirtualFile basePath(String path, VirtualFile config, Project project) {
        if (path.startsWith("/")) return LocalFileSystem.getInstance().findFileByPath(path);
        VirtualFile app = YiiContext.applicationRoot(config, project.getBasePath());
        if (app == null) return null;
        if (path.equals("@app") || path.startsWith("@app/")) return app.findFileByRelativePath(path.substring(4).replaceFirst("^/", ""));
        if ((path.equals("@common") || path.startsWith("@common/")) && app.getParent() != null) {
            VirtualFile common = app.getName().equals("common") ? app : app.getParent().findChild("common");
            return common == null ? null : common.findFileByRelativePath(path.substring(7).replaceFirst("^/", ""));
        }
        return null;
    }

    /** Locale directories of a base path, the application language first. */
    static List<VirtualFile> locales(VirtualFile basePath, @Nullable String language) {
        List<VirtualFile> locales = new ArrayList<>();
        for (VirtualFile child : basePath.getChildren()) if (child.isDirectory()) locales.add(child);
        locales.sort(Comparator.comparing((VirtualFile dir) -> !dir.getName().equals(language)).thenComparing(VirtualFile::getName));
        return locales;
    }
}
