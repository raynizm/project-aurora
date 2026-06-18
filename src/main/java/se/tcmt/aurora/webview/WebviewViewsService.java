// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.webview;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages all webview view instances (side panels) for the plugin.
 * Mirrors the reference plugin's MainThreadWebviewViewsShape and WebViewManager patterns.
 */
@Service(Service.Level.PROJECT)
public final class WebviewViewsService implements com.intellij.openapi.Disposable {
    private static final Logger LOG = Logger.getInstance(WebviewViewsService.class);

    private final Project project;
    private final Map<String, WebViewInstance> views = new HashMap<>();
    private final List<WebviewViewProviderData> providers = new ArrayList<>();
    private volatile WebViewInstance latestWebView;

    public WebviewViewsService(@NotNull Project project) {
        this.project = project;
        LOG.debug("Initialized WebviewViewsService for project: " + project.getName());
    }

    /**
     * Register a webview view provider.
     */
    public void registerProvider(@NotNull WebviewViewProviderData data) {
        LOG.debug("Registering webview view provider: viewType=" + data.getViewType());
        
        providers.add(data);
        
        // Create the WebView instance
        String viewId = UUID.randomUUID().toString();
        String title = data.getTitle();
        Map<String, Object> state = data.getState();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> extensionData = (Map<String, Object>) data.getExtension();
        
        WebViewInstance webView = new WebViewInstance(
            data.getViewType(), viewId, title, state, project, extensionData);
        
        views.put(viewId, webView);
        latestWebView = webView;
        
        LOG.debug("Created webview instance: viewType=" + data.getViewType() + ", viewId=" + viewId);
    }

    /**
     * Unregister a webview view provider by view type.
     */
    public void unregisterProvider(@NotNull String viewType) {
        LOG.debug("Unregistering webview view provider: viewType=" + viewType);
        
        // Find and remove the provider
        providers.removeIf(p -> p.getViewType().equals(viewType));
        
        // Remove associated views
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, WebViewInstance> entry : views.entrySet()) {
            if (entry.getValue().getViewType().equals(viewType)) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String viewId : toRemove) {
            WebViewInstance webView = views.remove(viewId);
            if (webView != null) {
                webView.dispose();
            }
        }
    }

    /**
     * Set webview view title.
     */
    public void setViewTitle(@NotNull String handle, @Nullable String value) {
        WebViewInstance webView = views.get(handle);
        if (webView != null) {
            webView.setTitle(value);
            LOG.debug("Set webview title: handle=" + handle + ", title=" + value);
        } else {
            LOG.warn("Webview not found for handle: " + handle);
        }
    }

    /**
     * Set webview view description.
     */
    public void setViewDescription(@NotNull String handle, @Nullable String value) {
        WebViewInstance webView = views.get(handle);
        if (webView != null) {
            // Store description in state
            webView.setState("description", value);
            LOG.debug("Set webview description: handle=" + handle + ", description=" + value);
        } else {
            LOG.warn("Webview not found for handle: " + handle);
        }
    }

    /**
     * Set webview view badge.
     */
    public void setViewBadge(@NotNull String handle, @Nullable Map<String, Object> badge) {
        WebViewInstance webView = views.get(handle);
        if (webView != null) {
            // Store badge in state
            if (badge != null) {
                webView.setState("badge", badge);
            } else {
                webView.setState("badge", null);
            }
            LOG.debug("Set webview badge: handle=" + handle + ", badge=" + badge);
        } else {
            LOG.warn("Webview not found for handle: " + handle);
        }
    }

    /**
     * Show a webview view.
     */
    public void showView(@NotNull String handle, boolean preserveFocus) {
        WebViewInstance webView = views.get(handle);
        if (webView != null) {
            LOG.debug("Showing webview: handle=" + handle + ", preserveFocus=" + preserveFocus);
            // In a full implementation, this would bring the view to front
        } else {
            LOG.warn("Webview not found for show: " + handle);
        }
    }

    /**
     * Get all registered views.
     */
    public @NotNull Map<String, WebViewInstance> getViews() {
        return new HashMap<>(views);
    }

    /**
     * Get a specific view by ID.
     */
    public @Nullable WebViewInstance getView(@NotNull String handle) {
        return views.get(handle);
    }

    /**
     * Get the latest created webview.
     */
    public @Nullable WebViewInstance getLatestWebView() {
        return latestWebView;
    }

    /**
     * Get all registered providers.
     */
    public @NotNull List<WebviewViewProviderData> getProviders() {
        return new ArrayList<>(providers);
    }

    /**
     * Create a webview view with custom HTML content.
     */
    public @NotNull WebViewInstance createCustomView(@NotNull String viewType, 
                                                      @NotNull String title,
                                                      @Nullable String htmlContent) {
        String viewId = UUID.randomUUID().toString();
        
        Map<String, Object> extensionData = new HashMap<>();
        extensionData.put("viewType", viewType);
        
        WebViewInstance webView = new WebViewInstance(
            viewType, viewId, title, null, project, extensionData);
        
        views.put(viewId, webView);
        latestWebView = webView;
        
        // Load HTML content if provided
        if (htmlContent != null && !htmlContent.isEmpty()) {
            webView.loadContent(htmlContent);
        }
        
        LOG.debug("Created custom webview: viewType=" + viewType + ", viewId=" + viewId);
        return webView;
    }

    @Override
    public void dispose() {
        LOG.debug("Disposing WebviewViewsService");
        
        // Dispose all views
        for (WebViewInstance webView : views.values()) {
            webView.dispose();
        }
        views.clear();
        providers.clear();
        latestWebView = null;
    }
}
