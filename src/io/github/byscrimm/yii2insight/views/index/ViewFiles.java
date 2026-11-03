package io.github.byscrimm.yii2insight.views.index;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import io.github.byscrimm.yii2insight.views.entities.ViewInfo;
import io.github.byscrimm.yii2insight.views.entities.ViewResolve;
import io.github.byscrimm.yii2insight.views.util.ViewUtil;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/**
 * View lookup over {@link ViewFileIndex}. The index stores only file content facts keyed by file
 * name; Yii root, application and theme path maps come from the current settings at query time.
 */
public final class ViewFiles {
    private ViewFiles() {}

    /** Views whose resolved key equals {@code key}, e.g. {@code @app/views/site/index.php}. */
    @NotNull
    public static List<ViewInfo> find(@NotNull Project project, @NotNull String key, @NotNull GlobalSearchScope scope) {
        List<ViewInfo> result = new ArrayList<>();
        String name = key.substring(key.lastIndexOf('/') + 1);
        FileBasedIndex.getInstance().processValues(ViewFileIndex.identity, name, null, (file, info) -> {
            ProgressManager.checkCanceled();
            ViewResolve resolve = ViewUtil.resolveView(file, project);
            if (resolve != null && keys(resolve).contains(key)) result.add(resolved(file, info, resolve));
            return true;
        }, scope);
        return result;
    }

    /** All views by resolved key whose key starts with {@code prefix}. */
    @NotNull
    public static Map<String, List<ViewInfo>> all(@NotNull Project project, @NotNull String prefix, @NotNull GlobalSearchScope scope) {
        FileBasedIndex index = FileBasedIndex.getInstance();
        Set<String> names = new HashSet<>();
        index.processAllKeys(ViewFileIndex.identity, name -> { names.add(name); return true; }, scope, null);
        Map<String, List<ViewInfo>> result = new TreeMap<>();
        for (String name : names) {
            ProgressManager.checkCanceled();
            index.processValues(ViewFileIndex.identity, name, null, (file, info) -> {
                ViewResolve resolve = ViewUtil.resolveView(file, project);
                if (resolve == null) return true;
                for (String key : keys(resolve))
                    if (key.startsWith(prefix)) result.computeIfAbsent(key, k -> new ArrayList<>()).add(resolved(file, info, resolve));
                return true;
            }, scope);
        }
        return result;
    }

    /** Module and widget views are also addressable through {@code @app/views/modules|widgets}. */
    private static List<String> keys(ViewResolve resolve) {
        List<String> keys = new ArrayList<>(List.of(resolve.key));
        if (resolve.key.startsWith("@app/modules/") && !resolve.relativePath.startsWith("/modules/"))
            keys.add("@app/views/modules" + resolve.key.substring(12));
        if (resolve.key.startsWith("@app/widgets/") && !resolve.relativePath.startsWith("/widgets/"))
            keys.add("@app/views/widgets" + resolve.key.substring(12));
        return keys;
    }

    private static ViewInfo resolved(VirtualFile file, ViewInfo info, ViewResolve resolve) {
        ViewInfo view = new ViewInfo();
        view.fileUrl = file.getUrl();
        view.application = resolve.application;
        view.theme = resolve.theme;
        view.parameters = info.parameters;
        return view;
    }
}
