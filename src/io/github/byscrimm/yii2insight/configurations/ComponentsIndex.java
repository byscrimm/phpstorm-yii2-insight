package io.github.byscrimm.yii2insight.configurations;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.php.lang.PhpFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public class ComponentsIndex extends FileBasedIndexExtension<String, String> {
    public static final ID<String, String> identity = ID.create("io.github.byscrimm.yii2insight.ComponentsIndex");

    @Override
    public @NotNull ID<String, String> getName() {
        return identity;
    }

    @Override
    public @NotNull DataIndexer<String, String, FileContent> getIndexer() {
        return file -> {
            PsiFile psiFile = file.getPsiFile();
            var parent = file.getFile().getParent();
            return parent == null ? Map.of() : ConfigEntries.of(psiFile, parent.getPath());
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 8;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
        return file -> (
                file.getFileType() == PhpFileType.INSTANCE
                && file.getUrl().contains("/config/")
                && !file.getUrl().contains("/environments/")
        );
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
