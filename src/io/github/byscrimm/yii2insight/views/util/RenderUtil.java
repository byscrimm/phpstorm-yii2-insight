package io.github.byscrimm.yii2insight.views.util;

import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.resolve.types.PhpType;
import io.github.byscrimm.yii2insight.utils.Yii2InsightSettings;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class RenderUtil {
    @NotNull
    public static Map<String, PhpType> getViewArguments(MethodReference reference) {
        final Map<String, PhpType> result = new LinkedHashMap<>();
        result.put("this", new PhpType.PhpTypeBuilder().add(Yii2InsightSettings.getInstance(reference.getProject()).defaultViewClass).build());

        for (RenderParameters.Entry entry : RenderParameters.of(reference).entries()) {
            // compact() names without a local definition keep an unknown type.
            if (entry.value() instanceof PhpTypedElement value) {
                result.put(entry.name(), value.getType().global(reference.getProject()));
            } else {
                result.put(entry.name(), PhpType.MIXED);
            }
        }

        return result;
    }
}
