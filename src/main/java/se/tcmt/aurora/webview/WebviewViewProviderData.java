// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.webview;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Data class holding webview view provider registration information.
 * Mirrors the reference plugin's WebviewViewProviderData.
 */
public class WebviewViewProviderData {
    private final Map<String, Object> extension;
    private final String viewType;
    private final Map<String, Object> options;

    public WebviewViewProviderData(@Nullable Map<String, Object> extension, 
                                   @NotNull String viewType,
                                   @Nullable Map<String, Object> options) {
        this.extension = extension != null ? new HashMap<>(extension) : new HashMap<>();
        this.viewType = viewType;
        this.options = options != null ? new HashMap<>(options) : new HashMap<>();
    }

    /**
     * Get the extension data.
     */
    public @NotNull Map<String, Object> getExtension() {
        return extension;
    }

    /**
     * Get the view type (e.g., "aurora-task-list", "aurora-file-explorer").
     */
    public @NotNull String getViewType() {
        return viewType;
    }

    /**
     * Get the options map.
     */
    public @NotNull Map<String, Object> getOptions() {
        return options;
    }

    /**
     * Get a specific option value.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOption(@NotNull String key, @Nullable T defaultValue) {
        Object value = options.get(key);
        if (value != null && defaultValue != null && !defaultValue.getClass().isInstance(value)) {
            return defaultValue;
        }
        return value != null ? (T) value : defaultValue;
    }

    /**
     * Get the view title from options, falling back to viewType.
     */
    public @NotNull String getTitle() {
        Object title = options.get("title");
        if (title instanceof String && !((String) title).isEmpty()) {
            return (String) title;
        }
        return viewType;
    }

    /**
     * Get the view state from options.
     */
    public @NotNull Map<String, Object> getState() {
        Object state = options.get("state");
        if (state instanceof Map) {
            return (Map<String, Object>) state;
        }
        return new HashMap<>();
    }
}
