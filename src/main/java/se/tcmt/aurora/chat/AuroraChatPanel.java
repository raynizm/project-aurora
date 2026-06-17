package se.tcmt.aurora.chat;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.browser.CefFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chat panel using JCEF WebView for rendering the Aurora chat UI.
 * Follows the Roo-Code-JetBrains-CE pattern: JBCefBrowser + JBCefJSQuery bridge.
 */
public class AuroraChatPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(AuroraChatPanel.class);

    private final Project project;
    private volatile Editor currentEditor;
    private volatile se.tcmt.aurora.provider.AiProvider activeProvider;
    private volatile se.tcmt.aurora.settings.AuroraSettingsState settingsState;

    // Chat history — tracked in Java for API calls (Roo-Code pattern)
    private final List<ChatMessage> chatHistory = new CopyOnWriteArrayList<>();

    // JCEF browser instance
    private JBCefBrowser browser;
    private JBCefJSQuery jsQuery;

    // Theme sync — monitors IDE dark/light changes and pushes CSS vars to WebView
    private se.tcmt.aurora.theme.ThemeSync themeSync;

    public AuroraChatPanel(@NotNull Project project) {
        this.project = project;
        setLayout(new BorderLayout());
        setBackground(UIUtil.getPanelBackground());

        createWebView();
    }

    /**
     * Initialize the JCEF browser and load chat.html from resources.
     */
    private void createWebView() {
        try {
            String htmlContent = loadHtmlFromResources("chat.html");

            if (htmlContent == null) {
                LOG.error("Failed to load chat.html from resources");
                add(createErrorPanel("Failed to load chat interface. Please check plugin resources."), BorderLayout.CENTER);
                return;
            }

            // Create JBCefBrowser using Roo-Code pattern
            browser = JBCefBrowser.createBuilder()
                    .setOffScreenRendering(false)
                    .build();

            // Set background color to match IDE theme (Roo-Code pattern)
            browser.getComponent().setBackground(UIUtil.getPanelBackground());

            // Set up JS bridge for receiving messages from JavaScript
            setupJsBridge();

            // Initialize theme sync — detects IDE dark/light and pushes CSS vars to WebView
            initThemeSync();

            // Register load handler using CEF's native adapter (Roo-Code pattern)
            browser.getJBCefClient().addLoadHandler(new CefLoadHandlerAdapter() {
                @Override
                public void onLoadEnd(org.cef.browser.CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
                    injectJsBridge();
                }
            }, browser.getCefBrowser());

            browser.loadHTML(htmlContent);

            add(browser.getComponent(), BorderLayout.CENTER);

        } catch (Exception e) {
            LOG.error("Failed to create WebView chat panel", e);
            add(createErrorPanel("Failed to initialize chat: " + e.getMessage()), BorderLayout.CENTER);
        }
    }

    /**
     * Load HTML content from classpath resources.
     */
    @Nullable
    private String loadHtmlFromResources(@NotNull String resourceName) {
        try (java.io.InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            LOG.error("Error loading resource: " + resourceName, e);
            return null;
        }
    }

    /**
     * Set up the JBCefJSQuery to receive messages from JavaScript.
     */
    private void setupJsBridge() {
        try {
            jsQuery = JBCefJSQuery.create((com.intellij.ui.jcef.JBCefBrowserBase) browser);
            jsQuery.addHandler(message -> {
                handleMessageFromWebview(message);
                return null;
            });
            LOG.debug("JCEF JS bridge setup successful");
        } catch (Exception e) {
            LOG.error("Failed to setup JCEF JS bridge", e);
        }
    }

    /**
     * Inject the sendMessageToPlugin function into the loaded page.
     */
    private void injectJsBridge() {
        if (jsQuery == null) {
            LOG.warn("JS query is null, cannot inject bridge");
            return;
        }

        String jsQueryFunction = jsQuery.inject("msgStr");
        if (jsQueryFunction != null) {
            String script = """
                window.Aurora = {
                    postMessage: function(message) {
                        try {
                            const msgStr = JSON.stringify(message);
                            %s;
                        } catch (e) {
                            console.warn("Failed to send message to plugin", e);
                        }
                    }
                };
                console.log("Aurora JCEF Bridge injected");
            """.formatted(jsQueryFunction);

            browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0);
        } else {
            LOG.warn("jsQuery inject returned null, cannot inject bridge");
        }
    }

    /**
     * Handle messages received from the webview JavaScript.
     */
    private void handleMessageFromWebview(@NotNull String message) {
        try {
            com.google.gson.JsonElement json = new com.google.gson.Gson().fromJson(message, com.google.gson.JsonElement.class);
            com.google.gson.JsonObject obj = json.getAsJsonObject();

            String type = obj.get("type").getAsString();

            switch (type) {
                case "message":
                    handleUserMessage(obj.get("content").getAsString());
                    break;
                default:
                    LOG.debug("Unknown message type from webview: " + type);
            }
        } catch (Exception e) {
            LOG.error("Error handling message from webview", e);
        }
    }

    /**
     * Initialize theme sync — monitors IDE dark/light changes and pushes CSS vars to WebView.
     */
    private void initThemeSync() {
        try {
            themeSync = new se.tcmt.aurora.theme.ThemeSync(project);
            themeSync.registerListener((isDark, cssVars) -> {
                LOG.debug("Theme changed: " + (isDark ? "dark" : "light") + ", vars=" + cssVars.size());
                sendThemeToWebview(isDark, cssVars);
            });
        } catch (Exception e) {
            LOG.error("Failed to initialize theme sync", e);
        }
    }

    /**
     * Send theme CSS variables to the webview.
     */
    private void sendThemeToWebview(boolean isDark, @NotNull Map<String, String> cssVars) {
        if (browser == null || browser.getCefBrowser() == null) return;

        // Build JSON object of CSS vars
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : cssVars.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");

        SwingUtilities.invokeLater(() -> {
            String script = """
                if (window.Aurora && window.Aurora.onThemeChange) {
                    window.Aurora.onThemeChange(%s, %b);
                }
            """.formatted(json.toString(), isDark);

            browser.getCefBrowser().executeJavaScript(
                    script,
                    browser.getCefBrowser().getURL(),
                    0
            );
        });
    }

    /**
     * Handle a user message sent from the webview.
     */
    private void handleUserMessage(@NotNull String text) {
        if (text.isEmpty()) return;

        // Track user message in history
        ChatMessage userMsg = new ChatMessage(ChatMessage.Role.USER, text);
        chatHistory.add(userMsg);

        // Extract context from current editor
        se.tcmt.aurora.context.CodeContextExtractor.Context context =
                se.tcmt.aurora.context.CodeContextExtractor.extractContext(project, currentEditor);

        String fullMessage = text;
        if (context.hasContext()) {
            fullMessage = context.formatForPrompt() + text;
        }

        // Send "thinking" status to webview
        sendToWebview("{\"type\":\"status\",\"content\":\"Aurora is thinking...\"}");

        // Process in background thread
        new Thread(() -> {
            try {
                if (settingsState == null || !settingsState.getApiKey().isEmpty()) {
                    // Check API key from settings
                    String apiKey = settingsState != null ? settingsState.getApiKey() : "";
                    if (apiKey.isEmpty()) {
                        sendToWebview("{\"type\":\"error\",\"message\":\"Please configure your API key in Settings > Tools > Aurora.\"}");
                        return;
                    }

                    // Build full history with context for this message
                    List<ChatMessage> history = buildHistoryWithContext(context);

                    if (activeProvider == null) {
                        sendToWebview("{\"type\":\"error\",\"message\":\"No AI provider configured.\"}");
                        return;
                    }

                    // Call provider with streaming callback — tokens sent incrementally to webview
                    StringBuilder fullResponseBuilder = new StringBuilder();

                    String fullResponse = activeProvider.chatStream(history, settingsState.toProviderConfig(), delta -> {
                        if (delta != null && !delta.isEmpty()) {
                            fullResponseBuilder.append(delta);
                            // Send token update to webview for incremental display
                            sendToWebview("{\"type\":\"token\",\"content\":" + escapeJson(delta) + "}");
                        }
                    });

                    String finalResponse = fullResponseBuilder.toString();

                    if (finalResponse != null && !finalResponse.isEmpty()) {
                        // Track assistant message in history
                        ChatMessage assistantMsg = new ChatMessage(ChatMessage.Role.ASSISTANT, finalResponse);
                        chatHistory.add(assistantMsg);

                        // Send final response to webview (full accumulated text)
                        sendToWebview("{\"type\":\"response\",\"content\":" + escapeJson(finalResponse) + "}");
                    } else {
                        sendToWebview("{\"type\":\"error\",\"message\":\"Sorry, I couldn't get a response.\"}");
                    }

                } else {
                    sendToWebview("{\"type\":\"error\",\"message\":\"Please configure your API key in Settings > Tools > Aurora.\"}");
                }

            } catch (Exception e) {
                LOG.error("Error calling AI provider", e);
                sendToWebview("{\"type\":\"error\",\"message\":" + escapeJson(e.getMessage()) + "}");
            }
        }).start();
    }

    /**
     * Build chat history with context injected as a system message.
     */
    private List<ChatMessage> buildHistoryWithContext(se.tcmt.aurora.context.CodeContextExtractor.Context context) {
        List<ChatMessage> history = new ArrayList<>(chatHistory);

        // Inject context as a system message if available
        if (context.hasContext()) {
            String contextPrompt = "You are an AI coding assistant integrated into IntelliJ IDEA.\n\n" +
                    "The user has the following code context available:\n" +
                    context.formatForPrompt() + "\n\nPlease use this context when answering.";

            ChatMessage systemMsg = new ChatMessage(ChatMessage.Role.SYSTEM, contextPrompt);
            history.add(0, systemMsg); // Insert at beginning
        }

        return history;
    }

    /**
     * Send a message to the webview JavaScript.
     */
    private void sendToWebview(@NotNull String jsonMessage) {
        if (browser == null || browser.getCefBrowser() == null) return;

        SwingUtilities.invokeLater(() -> {
            String script = """
                if (window.Aurora && window.Aurora.onMessage) {
                    window.Aurora.onMessage(%s);
                } else {
                    console.warn("Aurora.onMessage not available");
                }
            """.formatted(jsonMessage);

            browser.getCefBrowser().executeJavaScript(
                    script,
                    browser.getCefBrowser().getURL(),
                    0
            );
        });
    }

    /**
     * Escape a string for safe JSON embedding.
     */
    private static String escapeJson(@NotNull String text) {
        return "\"" + text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /**
     * Set the current editor for context extraction.
     */
    public void setCurrentEditor(@Nullable Editor editor) {
        this.currentEditor = editor;
    }

    public void setActiveProvider(se.tcmt.aurora.provider.AiProvider provider) {
        this.activeProvider = provider;
    }

    /**
     * Set the settings state for dynamic configuration.
     */
    public void setSettingsState(se.tcmt.aurora.settings.AuroraSettingsState settings) {
        this.settingsState = settings;
    }

    /**
     * Clear the chat in the webview and reset history.
     */
    public void clearChat() {
        chatHistory.clear();
        sendToWebview("{\"type\":\"clear\"}");
    }

    /**
     * Get the current chat history (for debugging/testing).
     */
    @NotNull
    public List<ChatMessage> getChatHistory() {
        return new ArrayList<>(chatHistory);
    }

    /**
     * Create an error panel when WebView fails to load.
     */
    private JPanel createErrorPanel(@NotNull String message) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(JBUI.Borders.empty(20));
        panel.setBackground(Color.WHITE);

        JLabel label = new JLabel("<html><div style='text-align:center;color:#7a7d80;'>" + message + "</div></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);

        return panel;
    }
}
