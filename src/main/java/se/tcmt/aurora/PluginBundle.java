package se.tcmt.aurora;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class PluginBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "messages.PluginBundle";

    public PluginBundle() {
        super(BUNDLE);
    }

    @Override
    @PropertyKey(resourceBundle = BUNDLE)
    public @NotNull String getMessage(@NotNull String key, Object... params) {
        return super.getMessage(key, params);
    }
}
