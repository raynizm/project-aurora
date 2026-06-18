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

    // Webview views service — manages side panel webviews
    private se.tcmt.aurora.webview.WebviewViewsService webviewViewsService;

    // Secret state service — manages secure storage
    private se.tcmt.aurora.secret.SecretStateService secretStateService;

    // Telemetry service — logs events and metrics
    private se.tcmt.aurora.telemetry.TelemetryService telemetryService;

    // Logger service — manages log files and output channels
    private se.tcmt.aurora.logger.LoggerService loggerService;

    // Output channel service — manages IDE output channels
    private se.tcmt.aurora.logger.OutputChannelService outputChannelService;

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
        initWebviewViewsService();
        initSecretStateService();
        initTelemetryService();
        initLoggerService();
        initOutputChannelService();
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
                case "webview":
                    handleWebviewCommand(obj);
                    break;
                case "secret":
                    handleSecretCommand(obj);
                    break;
                case "telemetry":
                    handleTelemetryCommand(obj);
                    break;
                case "logger":
                    handleLoggerCommand(obj);
                    break;
                case "output":
                    handleOutputCommand(obj);
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
     * Initialize webview views service for side panel functionality.
     */
    private void initWebviewViewsService() {
        try {
            webviewViewsService = new se.tcmt.aurora.webview.WebviewViewsService(project);
            LOG.debug("WebviewViewsService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize WebviewViewsService", e);
        }
    }

    /**
     * Initialize secret state service for secure storage.
     */
    private void initSecretStateService() {
        try {
            secretStateService = new se.tcmt.aurora.secret.SecretStateService();
            LOG.debug("SecretStateService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize SecretStateService", e);
        }
    }

    /**
     * Initialize telemetry service for event logging.
     */
    private void initTelemetryService() {
        try {
            telemetryService = new se.tcmt.aurora.telemetry.TelemetryService();
            LOG.debug("TelemetryService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize TelemetryService", e);
        }
    }

    /**
     * Initialize logger service for log file management.
     */
    private void initLoggerService() {
        try {
            loggerService = new se.tcmt.aurora.logger.LoggerService();
            LOG.debug("LoggerService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize LoggerService", e);
        }
    }

    /**
     * Initialize output channel service for IDE output channels.
     */
    private void initOutputChannelService() {
        try {
            outputChannelService = new se.tcmt.aurora.logger.OutputChannelService();
            LOG.debug("OutputChannelService initialized");
        } catch (Exception e) {
            LOG.error("Failed to initialize OutputChannelService", e);
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
     * Handle webview command from JS query.
     */
    private void handleWebviewCommand(@NotNull com.google.gson.JsonObject obj) {
        if (webviewViewsService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Webview views service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            switch (action) {
                case "register":
                    handleRegisterWebview(obj);
                    break;
                case "setTitle":
                    handleSetWebviewTitle(obj);
                    break;
                case "setDescription":
                    handleSetWebviewDescription(obj);
                    break;
                case "show":
                    handleShowWebview(obj);
                    break;
                default:
                    LOG.debug("Unknown webview action: " + action);
            }
        } catch (Exception e) {
            LOG.error("Failed to handle webview command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle webview command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle register webview view command.
     */
    private void handleRegisterWebview(@NotNull com.google.gson.JsonObject obj) {
        try {
            String viewType = obj.has("viewType") ? obj.get("viewType").getAsString() : "";
            
            if (viewType.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"View type is required\"}");
                return;
            }

            // Parse extension data and options from the request
            com.google.gson.JsonObject extensionData = obj.has("extension") ? 
                    obj.getAsJsonObject("extension") : new com.google.gson.JsonObject();
            
            com.google.gson.JsonObject options = obj.has("options") ? 
                    obj.getAsJsonObject("options") : new com.google.gson.JsonObject();

            // Convert JSON objects to Maps for WebviewViewProviderData
            Map<String, Object> extensionMap = convertJsonToMap(extensionData);
            Map<String, Object> optionsMap = convertJsonToMap(options);

            se.tcmt.aurora.webview.WebviewViewProviderData providerData = 
                    new se.tcmt.aurora.webview.WebviewViewProviderData(
                            extensionMap, viewType, optionsMap);

            webviewViewsService.registerProvider(providerData);
            
            sendToWebview("{\"type\":\"webviewRegistered\",\"viewType\":\"" + escapeJson(viewType) + "\"}");
            LOG.debug("Registered webview view: " + viewType);
        } catch (Exception e) {
            LOG.error("Failed to register webview", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to register webview: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle set webview title command.
     */
    private void handleSetWebviewTitle(@NotNull com.google.gson.JsonObject obj) {
        try {
            String handle = obj.has("handle") ? obj.get("handle").getAsString() : "";
            String title = obj.has("title") && !obj.get("title").isJsonNull() ? 
                    obj.get("title").getAsString() : null;

            webviewViewsService.setViewTitle(handle, title);
            
            sendToWebview("{\"type\":\"webviewTitleSet\",\"handle\":\"" + escapeJson(handle) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to set webview title", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to set webview title: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle set webview description command.
     */
    private void handleSetWebviewDescription(@NotNull com.google.gson.JsonObject obj) {
        try {
            String handle = obj.has("handle") ? obj.get("handle").getAsString() : "";
            String description = obj.has("description") && !obj.get("description").isJsonNull() ? 
                    obj.get("description").getAsString() : null;

            webviewViewsService.setViewDescription(handle, description);
            
            sendToWebview("{\"type\":\"webviewDescriptionSet\",\"handle\":\"" + escapeJson(handle) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to set webview description", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to set webview description: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle show webview command.
     */
    private void handleShowWebview(@NotNull com.google.gson.JsonObject obj) {
        try {
            String handle = obj.has("handle") ? obj.get("handle").getAsString() : "";
            boolean preserveFocus = obj.has("preserveFocus") && !obj.get("preserveFocus").isJsonNull() ? 
                    obj.get("preserveFocus").getAsBoolean() : false;

            webviewViewsService.showView(handle, preserveFocus);
            
            sendToWebview("{\"type\":\"webviewShown\",\"handle\":\"" + escapeJson(handle) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to show webview", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to show webview: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle secret command from JS query.
     */
    private void handleSecretCommand(@NotNull com.google.gson.JsonObject obj) {
        if (secretStateService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Secret state service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";
            String extensionId = obj.has("extensionId") ? obj.get("extensionId").getAsString() : "";
            String key = obj.has("key") ? obj.get("key").getAsString() : "";

            switch (action) {
                case "get":
                    handleGetSecret(extensionId, key);
                    break;
                case "set":
                    String value = obj.has("value") && !obj.get("value").isJsonNull() ? 
                            obj.get("value").getAsString() : "";
                    handleSetSecret(extensionId, key, value);
                    break;
                case "delete":
                    handleDeleteSecret(extensionId, key);
                    break;
                default:
                    LOG.debug("Unknown secret action: " + action);
            }
        } catch (Exception e) {
            LOG.error("Failed to handle secret command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle secret command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle get secret command.
     */
    private void handleGetSecret(@NotNull String extensionId, @NotNull String key) {
        try {
            String value = secretStateService.getPassword(extensionId, key);
            
            if (value != null) {
                sendToWebview("{\"type\":\"secretRetrieved\",\"extensionId\":\"" + escapeJson(extensionId) + 
                        "\",\"key\":\"" + escapeJson(key) + "\",\"value\":\"" + escapeJson(value) + "\"}");
            } else {
                sendToWebview("{\"type\":\"secretNotFound\",\"extensionId\":\"" + escapeJson(extensionId) + 
                        "\",\"key\":\"" + escapeJson(key) + "\"}");
            }
        } catch (Exception e) {
            LOG.error("Failed to get secret", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to get secret: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle set secret command.
     */
    private void handleSetSecret(@NotNull String extensionId, @NotNull String key, 
                                  @NotNull String value) {
        try {
            secretStateService.setPassword(extensionId, key, value);
            
            sendToWebview("{\"type\":\"secretStored\",\"extensionId\":\"" + escapeJson(extensionId) + 
                    "\",\"key\":\"" + escapeJson(key) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to set secret", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to set secret: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle delete secret command.
     */
    private void handleDeleteSecret(@NotNull String extensionId, @NotNull String key) {
        try {
            secretStateService.deletePassword(extensionId, key);
            
            sendToWebview("{\"type\":\"secretDeleted\",\"extensionId\":\"" + escapeJson(extensionId) + 
                    "\",\"key\":\"" + escapeJson(key) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to delete secret", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to delete secret: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle telemetry command from JS query.
     */
    private void handleTelemetryCommand(@NotNull com.google.gson.JsonObject obj) {
        if (telemetryService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Telemetry service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            switch (action) {
                case "log":
                    handleLogEvent(obj);
                    break;
                default:
                    LOG.debug("Unknown telemetry action: " + action);
            }
        } catch (Exception e) {
            LOG.error("Failed to handle telemetry command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle telemetry command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle log event command.
     */
    private void handleLogEvent(@NotNull com.google.gson.JsonObject obj) {
        try {
            String eventName = obj.has("eventName") ? obj.get("eventName").getAsString() : "";
            
            if (eventName.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"Event name is required\"}");
                return;
            }

            // Convert optional data to Map
            Map<String, Object> eventData = null;
            if (obj.has("data") && !obj.get("data").isJsonNull()) {
                eventData = convertJsonToMap(obj.getAsJsonObject("data"));
            }

            telemetryService.publicLog(eventName, eventData);
            
            sendToWebview("{\"type\":\"eventLogged\",\"eventName\":\"" + escapeJson(eventName) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to log event", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to log event: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle logger command from JS query.
     */
    private void handleLoggerCommand(@NotNull com.google.gson.JsonObject obj) {
        if (loggerService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Logger service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            switch (action) {
                case "log":
                    handleLogToFile(obj);
                    break;
                case "createLogger":
                    handleCreateLogger(obj);
                    break;
                default:
                    LOG.debug("Unknown logger action: " + action);
            }
        } catch (Exception e) {
            LOG.error("Failed to handle logger command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle logger command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle log to file command.
     */
    private void handleLogToFile(@NotNull com.google.gson.JsonObject obj) {
        try {
            String filePath = obj.has("filePath") ? obj.get("filePath").getAsString() : "";
            
            if (filePath.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"File path is required\"}");
                return;
            }

            // Parse messages array
            List<String> messages = new ArrayList<>();
            if (obj.has("messages") && obj.get("messages").isJsonArray()) {
                com.google.gson.JsonArray msgArray = obj.getAsJsonArray("messages");
                for (com.google.gson.JsonElement elem : msgArray) {
                    messages.add(elem.getAsString());
                }
            }

            loggerService.logToFile(filePath, messages);
            
            sendToWebview("{\"type\":\"logged\",\"filePath\":\"" + escapeJson(filePath) + 
                    "\",\"messageCount\":" + messages.size() + "}");
        } catch (Exception e) {
            LOG.error("Failed to log to file", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to log to file: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle create logger command.
     */
    private void handleCreateLogger(@NotNull com.google.gson.JsonObject obj) {
        try {
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            
            if (name.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"Logger name is required\"}");
                return;
            }

            // Convert options to Map
            Map<String, Object> options = null;
            if (obj.has("options") && !obj.get("options").isJsonNull()) {
                options = convertJsonToMap(obj.getAsJsonObject("options"));
            }

            se.tcmt.aurora.logger.LoggerService.LogChannel channel = 
                    loggerService.createLogger(name, options);

            if (channel != null) {
                sendToWebview("{\"type\":\"loggerCreated\",\"name\":\"" + escapeJson(name) + "\"}");
            } else {
                sendToWebview("{\"type\":\"error\",\"message\":\"Failed to create logger: " + name + "\"}");
            }
        } catch (Exception e) {
            LOG.error("Failed to create logger", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to create logger: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle output command from JS query.
     */
    private void handleOutputCommand(@NotNull com.google.gson.JsonObject obj) {
        if (outputChannelService == null) {
            sendToWebview("{\"type\":\"error\",\"message\":\"Output channel service not available\"}");
            return;
        }

        try {
            String action = obj.has("action") ? obj.get("action").getAsString() : "";

            switch (action) {
                case "register":
                    handleRegisterOutputChannel(obj);
                    break;
                case "reveal":
                    handleRevealOutputChannel(obj);
                    break;
                case "close":
                    handleCloseOutputChannel(obj);
                    break;
                default:
                    LOG.debug("Unknown output action: " + action);
            }
        } catch (Exception e) {
            LOG.error("Failed to handle output command", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to handle output command: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle register output channel command.
     */
    private void handleRegisterOutputChannel(@NotNull com.google.gson.JsonObject obj) {
        try {
            String label = obj.has("label") ? obj.get("label").getAsString() : "";
            String extensionId = obj.has("extensionId") ? obj.get("extensionId").getAsString() : "";

            if (label.isEmpty()) {
                sendToWebview("{\"type\":\"error\",\"message\":\"Label is required\"}");
                return;
            }

            // Parse file components and language ID from the request
            Map<String, Object> fileComponents = null;
            if (obj.has("file") && !obj.get("file").isJsonNull()) {
                fileComponents = convertJsonToMap(obj.getAsJsonObject("file"));
            }

            String languageId = obj.has("languageId") && !obj.get("languageId").isJsonNull() ? 
                    obj.get("languageId").getAsString() : null;

            String channelId = outputChannelService.register(label, fileComponents, languageId, extensionId);
            
            sendToWebview("{\"type\":\"outputChannelRegistered\",\"channelId\":\"" + escapeJson(channelId) + 
                    "\",\"label\":\"" + escapeJson(label) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to register output channel", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to register output channel: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle reveal output channel command.
     */
    private void handleRevealOutputChannel(@NotNull com.google.gson.JsonObject obj) {
        try {
            String channelId = obj.has("channelId") ? obj.get("channelId").getAsString() : "";
            boolean preserveFocus = obj.has("preserveFocus") && !obj.get("preserveFocus").isJsonNull() ? 
                    obj.get("preserveFocus").getAsBoolean() : false;

            outputChannelService.reveal(channelId, preserveFocus);
            
            sendToWebview("{\"type\":\"outputChannelRevealed\",\"channelId\":\"" + escapeJson(channelId) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to reveal output channel", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to reveal output channel: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Handle close output channel command.
     */
    private void handleCloseOutputChannel(@NotNull com.google.gson.JsonObject obj) {
        try {
            String channelId = obj.has("channelId") ? obj.get("channelId").getAsString() : "";

            outputChannelService.close(channelId);
            
            sendToWebview("{\"type\":\"outputChannelClosed\",\"channelId\":\"" + escapeJson(channelId) + "\"}");
        } catch (Exception e) {
            LOG.error("Failed to close output channel", e);
            sendToWebview("{\"type\":\"error\",\"message\":\"Failed to close output channel: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Convert a JsonObject to a Map<String, Object> for service methods.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertJsonToMap(com.google.gson.JsonObject json) {
        Map<String, Object> map = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
            com.google.gson.JsonElement elem = entry.getValue();
            if (elem.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive prim = elem.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    map.put(entry.getKey(), prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    map.put(entry.getKey(), prim.getAsNumber());
                } else {
                    map.put(entry.getKey(), prim.getAsString());
                }
            } else if (elem.isJsonObject()) {
                map.put(entry.getKey(), convertJsonToMap(elem.getAsJsonObject()));
            } else if (elem.isJsonArray()) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement arrElem : elem.getAsJsonArray()) {
                    if (arrElem.isJsonPrimitive()) {
                        com.google.gson.JsonPrimitive prim = arrElem.getAsJsonPrimitive();
                        if (prim.isBoolean()) {
                            list.add(prim.getAsBoolean());
                        } else if (prim.isNumber()) {
                            list.add(prim.getAsNumber());
                        } else {
                            list.add(prim.getAsString());
                        }
                    } else if (arrElem.isJsonObject()) {
                        list.add(convertJsonToMap(arrElem.getAsJsonObject()));
                    }
                }
                map.put(entry.getKey(), list);
            }
        }
        return map;
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
