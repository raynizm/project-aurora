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

    // Terminal manager — manages terminal instances for command execution
    private se.tcmt.aurora.terminal.TerminalManager terminalManager;

    // Document manager — handles document sync and workspace edits
    private se.tcmt.aurora.document.DocumentManager documentManager;

    // Search service — handles file search across project
    private se.tcmt.aurora.search.SearchService searchService;

    // Clipboard service — handles read/write to system clipboard
    private se.tcmt.aurora.clipboard.ClipboardService clipboardService;

    // Tool service — manages language model tools for function calling
    private se.tcmt.aurora.tool.ToolService toolService;

    public AuroraChatPanel(@NotNull Project project) {
        this.project = project;
        setLayout(new BorderLayout());
        setBackground(UIUtil.getPanelBackground());

        createWebView();
        initTerminalManager();
        initDocumentManager();
        initSearchService();
        initClipboardService();
        initToolService();
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
                case "terminal":
                    handleTerminalCommand(obj);
                    break;
                case "search":
                    handleSearchCommand(obj);
                    break;
                case "clipboard":
                    handleClipboardCommand(obj);
                    break;
                case "tool":
                    handleToolCommand(obj);
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
                LOG.info("Theme changed callback: " + (isDark ? "dark" : "light") + ", vars=" + cssVars.size());
                for (Map.Entry<String, String> entry : cssVars.entrySet()) {
                    LOG.debug("  " + entry.getKey() + " = " + entry.getValue());
                }
                sendThemeToWebview(isDark, cssVars);
            });
        } catch (Exception e) {
            LOG.error("Failed to initialize theme sync", e);
        }
    }

    /**
     * Initialize terminal manager for command execution.
     */
    private void initTerminalManager() {
        try {
            terminalManager = project.getService(se.tcmt.aurora.terminal.TerminalManager.class);
            LOG.debug("TerminalManager initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize TerminalManager", e);
        }
    }

    /**
     * Initialize document manager for file sync and workspace edits.
     */
    private void initDocumentManager() {
        try {
            documentManager = project.getService(se.tcmt.aurora.document.DocumentManager.class);
            LOG.debug("DocumentManager initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize DocumentManager", e);
        }
    }

    /**
     * Initialize search service for file search across project.
     */
    private void initSearchService() {
        try {
            searchService = project.getService(se.tcmt.aurora.search.SearchService.class);
            LOG.debug("SearchService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize SearchService", e);
        }
    }

    /**
     * Initialize clipboard service for system clipboard operations.
     */
    private void initClipboardService() {
        try {
            clipboardService = new se.tcmt.aurora.clipboard.ClipboardService();
            LOG.debug("ClipboardService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize ClipboardService", e);
        }
    }

    /**
     * Initialize tool service for language model function calling.
     */
    private void initToolService() {
        try {
            toolService = project.getService(se.tcmt.aurora.tool.ToolService.class);
            LOG.debug("ToolService initialized with " + toolService.getToolCount() + " tools");
        } catch (Exception e) {
            LOG.error("Failed to initialize ToolService", e);
        }
    }

    /**
     * Send theme CSS variables to the webview.
     */
    private void sendThemeToWebview(boolean isDark, @NotNull Map<String, String> cssVars) {
        if (browser == null || browser.getCefBrowser() == null) {
            LOG.warn("Cannot send theme: browser not ready");
            return;
        }

        // Build JSON object of CSS vars
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : cssVars.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
            first = false;
        }
        json.append("}");

        LOG.info("Sending theme to webview: " + (isDark ? "dark" : "light") + ", vars=" + cssVars.size());

        SwingUtilities.invokeLater(() -> {
            String script = """
                if (window.Aurora && window.Aurora.onThemeChange) {
                    console.log('Aurora.onThemeChange called with', %s, %b);
                    window.Aurora.onThemeChange(%s, %b);
                } else {
                    console.error('Aurora.onThemeChange not available');
                }
            """.formatted(json.toString(), isDark, json.toString(), isDark);

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
        String contextPrompt = "You are an AI coding assistant integrated into IntelliJ IDEA.\n\n";

        if (context.hasContext()) {
            contextPrompt += "The user has the following code context available:\n" +
                    context.formatForPrompt() + "\n\nPlease use this context when answering.";
        }

        ChatMessage systemMsg = new ChatMessage(ChatMessage.Role.SYSTEM, contextPrompt);
        history.add(0, systemMsg); // Insert at beginning

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
     * Public method to send a message to the webview from external actions.
     */
    public void postMessage(@NotNull String jsonMessage) {
        sendToWebview(jsonMessage);
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
     * Handle terminal commands from webview (create, send command, etc.).
     */
    private void handleTerminalCommand(@NotNull com.google.gson.JsonObject obj) {
        String action = obj.has("action") ? obj.get("action").getAsString() : "";

        switch (action) {
            case "create":
                handleCreateTerminal(obj);
                break;
            case "send":
                handleSendCommand(obj);
                break;
            case "close":
                handleCloseTerminal();
                break;
            default:
                LOG.debug("Unknown terminal action: " + action);
        }
    }

    /**
     * Handle create terminal command.
     */
    private void handleCreateTerminal(@NotNull com.google.gson.JsonObject obj) {
        if (terminalManager == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Terminal service not available\"}");
            return;
        }

        try {
            String name = obj.has("name") ? obj.get("name").getAsString() : "Aurora Terminal";
            terminalManager.createTerminal(name);
            sendToWebview("{\"type\":\"terminalCreated\",\"message\":\"Terminal created: \" + \"" + name + "\"}");
            LOG.info("Terminal created via webview: " + name);
        } catch (Exception e) {
            LOG.error("Failed to create terminal", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to create terminal: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle send command to terminal.
     */
    private void handleSendCommand(@NotNull com.google.gson.JsonObject obj) {
        if (terminalManager == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Terminal service not available\"}");
            return;
        }

        try {
            String command = obj.has("command") ? obj.get("command").getAsString() : "";
            boolean shouldExecute = obj.has("execute") && obj.get("execute").getAsBoolean();

            terminalManager.sendTextToFirst(command, shouldExecute);
            sendToWebview("{\"type\":\"terminalOutput\",\"message\":\"Command sent: " + escapeJson(command) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to send command to terminal", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to send command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle close all terminals.
     */
    private void handleCloseTerminal() {
        if (terminalManager != null) {
            terminalManager.closeAllTerminals();
            sendToWebview("{\"type\":\"terminalsClosed\",\"message\":\"All terminals closed\"}");
        }
    }

    /**
     * Handle search commands from webview.
     */
    private void handleSearchCommand(@NotNull com.google.gson.JsonObject obj) {
        if (searchService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Search service not available\"}");
            return;
        }

        try {
            String pattern = obj.has("pattern") ? obj.get("pattern").getAsString() : "";
            boolean caseSensitive = obj.has("caseSensitive") && obj.get("caseSensitive").getAsBoolean();
            boolean useRegex = obj.has("useRegex") && obj.get("useRegex").getAsBoolean();

            if (pattern.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"Search pattern is empty\"}");
                return;
            }

            // Start search session
            se.tcmt.aurora.search.SearchSession session = searchService.startSearch(pattern, caseSensitive, useRegex);

            LOG.info("Search started: #" + session.getSessionId() + " pattern='" + pattern + "'");

            // Send initial response to webview
            sendToWebview("{\"type\":\"searchStarted\",\"sessionId\":" + session.getSessionId() + ",\"pattern\":\"" + escapeJson(pattern) + "\"}");

            // Poll for results (in a separate thread to avoid blocking)
            new Thread(() -> {
                try {
                    int pollCount = 0;
                    while (!session.isCompleted() && pollCount < 100) {
                        Thread.sleep(200);
                        pollCount++;

                        // Send progress update if callback is set
                        if (pollCount % 5 == 0) {
                            sendToWebview("{\"type\":\"searchProgress\",\"sessionId\":" + session.getSessionId() + ",\"filesScanned\":" + session.getFilesScanned() + "}");
                        }
                    }

                    // Get results when complete
                    java.util.List<se.tcmt.aurora.search.SearchResult> results = searchService.getResults(session.getSessionId());

                    if (results != null && !results.isEmpty()) {
                        // Build JSON array of results
                        StringBuilder jsonResults = new StringBuilder("[");
                        boolean first = true;
                        for (se.tcmt.aurora.search.SearchResult result : results) {
                            if (!first) jsonResults.append(",");
                            jsonResults.append("{")
                                    .append("\"file\":\"").append(escapeJson(result.getFilePath())).append("\",")
                                    .append("\"line\":").append(result.getLineNumber()).append(",")
                                    .append("\"column\":").append(result.getColumnNumber()).append(",")
                                    .append("\"matchedText\":\"").append(escapeJson(result.getMatchedText())).append("\",")
                                    .append("\"contextBefore\":\"").append(escapeJson(result.getContextBefore())).append("\",")
                                    .append("\"contextAfter\":\"").append(escapeJson(result.getContextAfter())).append("\"}")
                                    .append("}");
                            first = false;
                        }
                        jsonResults.append("]");

                        sendToWebview("{\"type\":\"searchComplete\",\"sessionId\":" + session.getSessionId() + ",\"results\":" + jsonResults.toString() + ",\"total\":" + results.size() + "}");
                    } else {
                        sendToWebview("{\"type\":\"searchComplete\",\"sessionId\":" + session.getSessionId() + ",\"results\":[],\"total\":0}");
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.debug("Search polling interrupted");
                }
            }).start();

        } catch (Exception e) {
            LOG.error("Failed to start search", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to start search: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle clipboard commands from webview (read, write).
     */
    private void handleClipboardCommand(@NotNull com.google.gson.JsonObject obj) {
        if (clipboardService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Clipboard service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            switch (action) {
                case "read":
                    handleReadClipboard();
                    break;
                case "write":
                    handleWriteClipboard(obj);
                    break;
                default:
                    LOG.debug("Unknown clipboard action: " + action);
                    sendToWebview("{\"type\":\"error\",\"message\":\"Unknown clipboard action: \" + action}");
            }
        } catch (Exception e) {
            LOG.error("Failed to handle clipboard command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle clipboard command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle read clipboard command.
     */
    private void handleReadClipboard() {
        try {
            String text = clipboardService.readText();
            
            if (text != null) {
                sendToWebview("{\"type\":\"clipboardContent\",\"content\":" + escapeJson(text) + "}");
                LOG.debug("[Clipboard] Read " + text.length() + " characters from system clipboard");
            } else {
                sendToWebview("{\"type\":\"clipboardEmpty\"}");
                LOG.debug("[Clipboard] Clipboard is empty or contains non-text data");
            }
        } catch (Exception e) {
            LOG.error("Failed to read clipboard", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to read clipboard: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle write clipboard command.
     */
    private void handleWriteClipboard(@NotNull com.google.gson.JsonObject obj) {
        try {
            String content = obj.has("content") ? obj.get("content").getAsString() : "";
            
            if (content.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"Content is empty\"}");
                return;
            }

            clipboardService.writeText(content);
            sendToWebview("{\"type\":\"clipboardWritten\",\"length\":" + content.length() + "}");
            LOG.debug("[Clipboard] Wrote " + content.length() + " characters to system clipboard");
        } catch (Exception e) {
            LOG.error("Failed to write to clipboard", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to write to clipboard: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle tool commands from webview (invoke, list tools).
     */
    private void handleToolCommand(@NotNull com.google.gson.JsonObject obj) {
        if (toolService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Tool service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            switch (action) {
                case "invoke":
                    handleInvokeTool(obj);
                    break;
                case "list":
                    handleListTools();
                    break;
                default:
                    LOG.debug("Unknown tool action: " + action);
                    sendToWebview("{\"type\":\"error\",\"message\":\"Unknown tool action: \" + action}");
            }
        } catch (Exception e) {
            LOG.error("Failed to handle tool command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle tool command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle invoke tool command.
     */
    private void handleInvokeTool(@NotNull com.google.gson.JsonObject obj) {
        try {
            String toolId = obj.has("toolId") ? obj.get("toolId").getAsString() : "";
            
            if (toolId.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"Tool ID is required\"}");
                return;
            }

            // Get parameters (optional)
            com.google.gson.JsonObject finalParams = null;
            if (obj.has("params") && !obj.get("params").isJsonNull()) {
                finalParams = obj.getAsJsonObject("params");
            }

            LOG.info("[Tool] Invoking tool: " + toolId);

            // Send "tool started" status to webview
            sendToWebview("{\"type\":\"toolStarted\",\"toolId\":\"" + escapeJson(toolId) + "\"}");

            // Invoke tool asynchronously
            invokeToolAsync(toolId, finalParams);

        } catch (Exception e) {
            LOG.error("Failed to invoke tool", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to invoke tool: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle list tools command.
     */
    private void handleListTools() {
        try {
            List<String> toolList = toolService.getTools();
            
            // Build JSON array of tools
            StringBuilder jsonTools = new StringBuilder("[");
            boolean first = true;
            for (String toolJson : toolList) {
                if (!first) jsonTools.append(",");
                jsonTools.append(toolJson);
                first = false;
            }
            jsonTools.append("]");

            sendToWebview("{\"type\":\"toolList\",\"tools\":" + jsonTools.toString() + 
                    ",\"count\":" + toolService.getToolCount() + "}");
            
            LOG.debug("[Tool] Listed " + toolService.getToolCount() + " tools for webview");
        } catch (Exception e) {
            LOG.error("Failed to list tools", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to list tools: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Invoke a tool asynchronously in a background thread.
     */
    private void invokeToolAsync(@NotNull String toolId, 
                                  @Nullable com.google.gson.JsonObject params) {
        new Thread(() -> {
            try {
                se.tcmt.aurora.tool.ToolInvocationResult result = 
                        toolService.invokeTool(toolId, params);

                if (result.isSuccess()) {
                    sendToWebview("{\"type\":\"toolComplete\",\"toolId\":\"" + escapeJson(toolId) + 
                            "\",\"success\":true,\"data\":" + escapeJson(result.toJson()) + "}");
                    LOG.info("[Tool] Tool '" + toolId + "' completed successfully");
                } else {
                    sendToWebview("{\"type\":\"toolError\",\"toolId\":\"" + escapeJson(toolId) + 
                            "\",\"message\":\"" + escapeJson(result.getMessage()) + "\"}");
                    LOG.warn("[Tool] Tool '" + toolId + "' failed: " + result.getMessage());
                }

            } catch (Exception e) {
                sendToWebview("{\"type\":\"toolError\",\"toolId\":\"" + escapeJson(toolId) + 
                        "\",\"message\":\"" + escapeJson(e.getMessage()) + "\"}");
                LOG.error("[Tool] Error invoking tool '" + toolId + "'", e);
            }
        }).start();
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
