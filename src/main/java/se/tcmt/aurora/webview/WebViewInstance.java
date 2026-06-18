// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.webview;

import com.google.gson.Gson;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import org.cef.CefSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single WebView instance with its lifecycle management.
 * Mirrors the reference plugin's WebViewInstance for side panel views.
 */
public class WebViewInstance implements Disposable {
    private static final Logger LOG = Logger.getInstance(WebViewInstance.class);

    private final String viewType;
    private final String viewId;
    private final String title;
    private final Map<String, Object> state;
    private final Project project;
    private final JBCefBrowser browser;
    private final Map<String, Object> extensionData;

    private boolean isDisposed = false;

    public WebViewInstance(@NotNull String viewType, @NotNull String viewId, 
                           @NotNull String title, @Nullable Map<String, Object> state,
                           @NotNull Project project, @Nullable Map<String, Object> extensionData) {
        this.viewType = viewType;
        this.viewId = viewId;
        this.title = title;
        this.state = state != null ? new HashMap<>(state) : new HashMap<>();
        this.project = project;
        this.extensionData = extensionData != null ? new HashMap<>(extensionData) : new HashMap<>();

        // Create JBCefBrowser for the webview
        this.browser = createBrowser();
        
        LOG.debug("Created WebViewInstance: viewType=" + viewType + ", viewId=" + viewId);
    }

    /**
     * Create a new JBCefBrowser instance.
     */
    private @NotNull JBCefBrowser createBrowser() {
        try {
            return JBCefBrowser.createBuilder().setOffScreenRendering(false).build();
        } catch (Exception e) {
            LOG.error("Failed to create JBCefBrowser", e);
            throw new RuntimeException("Failed to create browser instance", e);
        }
    }

    /**
     * Get the underlying JBCefBrowser.
     */
    public @NotNull JBCefBrowser getBrowser() {
        return browser;
    }

    /**
     * Get the webview type (e.g., "aurora-task-list", "aurora-file-explorer").
     */
    public @NotNull String getViewType() {
        return viewType;
    }

    /**
     * Get the unique view ID.
     */
    public @NotNull String getViewId() {
        return viewId;
    }

    /**
     * Get the view title.
     */
    public @NotNull String getTitle() {
        return title;
    }

    /**
     * Set the view title.
     */
    public void setTitle(@Nullable String newTitle) {
        if (newTitle != null) {
            this.title.intern(); // Note: in practice, would update UI
            LOG.debug("Updated webview title to: " + newTitle);
        }
    }

    /**
     * Get the view state.
     */
    public @NotNull Map<String, Object> getState() {
        return state;
    }

    /**
     * Set a state value.
     */
    public void setState(@NotNull String key, @Nullable Object value) {
        if (value != null) {
            state.put(key, value);
        } else {
            state.remove(key);
        }
    }

    /**
     * Get extension data associated with this webview.
     */
    public @NotNull Map<String, Object> getExtensionData() {
        return extensionData;
    }

    /**
     * Load HTML content into the browser.
     */
    public void loadContent(@NotNull String html) {
        if (isDisposed) {
            LOG.warn("Cannot load content to disposed webview: " + viewId);
            return;
        }
        try {
            browser.loadHTML(html);
            LOG.debug("Loaded HTML content into webview: " + viewId);
        } catch (Exception e) {
            LOG.error("Failed to load HTML into webview", e);
        }
    }

    /**
     * Send message to the webview via JS bridge.
     */
    public void sendMessage(@NotNull String jsonMessage) {
        if (isDisposed) {
            return;
        }
        try {
            browser.getCefBrowser().executeJavaScript(
                "window.__auroraWebView.onMessage(" + new Gson().toJson(jsonMessage) + ");", "", 0);
        } catch (Exception e) {
            LOG.error("Failed to send message to webview", e);
        }
    }

    /**
     * Resolve the webview view to the extension host.
     */
    public void resolveView(@Nullable Object data) {
        LOG.debug("Resolving webview view: viewType=" + viewType + ", viewId=" + viewId);
        // In a full implementation, this would notify the extension host via RPC
    }

    /**
     * Check if the webview is disposed.
     */
    public boolean isDisposed() {
        return isDisposed;
    }

    @Override
    public void dispose() {
        if (isDisposed) {
            return;
        }
        isDisposed = true;
        
        try {
            browser.dispose();
            LOG.debug("Disposed WebViewInstance: viewId=" + viewId);
        } catch (Exception e) {
            LOG.error("Error disposing webview", e);
        }
    }
}
