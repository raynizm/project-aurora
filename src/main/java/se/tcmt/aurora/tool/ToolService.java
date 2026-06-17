// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.tool;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Language model tools service for function calling.
 * Mirrors MainThreadLanguageModelTools from Roo-Code-JetBrains-CE reference plugin.
 * 
 * Provides:
 * - Tool registration/unregistration
 * - Tool invocation with parameter validation
 * - Token counting for tool inputs
 * - Integration with AI provider for function calling
 */
@Service(Service.Level.PROJECT)
public final class ToolService {

    private static final Logger LOG = Logger.getInstance(ToolService.class);

    private final Project project;
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    public ToolService(@NotNull Project project) {
        this.project = project;
        initDefaultTools();
        LOG.debug("ToolService initialized with " + tools.size() + " default tools");
    }

    /**
     * Initialize default tools that are always available.
     */
    private void initDefaultTools() {
        // File read tool - allows LLM to read file contents
        JsonObject readFileSchema = new JsonObject();
        readFileSchema.addProperty("type", "object");
        JsonObject readFileProps = new JsonObject();
        readFileProps.addProperty("path", "string");
        readFileSchema.add("properties", readFileProps);
        JsonArray readFileRequired = new JsonArray();
        readFileRequired.add("path");
        readFileSchema.add("required", readFileRequired);

        registerTool(new ToolDefinition(
            "read_file",
            "Read file contents",
            "Read the contents of a file from the project. Returns file content as text.",
            readFileSchema
        ));

        // File write tool - allows LLM to create/modify files
        JsonObject writeFileSchema = new JsonObject();
        writeFileSchema.addProperty("type", "object");
        JsonObject writeFileProps = new JsonObject();
        writeFileProps.addProperty("path", "string");
        writeFileProps.addProperty("content", "string");
        writeFileSchema.add("properties", writeFileProps);
        JsonArray writeFileRequired = new JsonArray();
        writeFileRequired.add("path");
        writeFileRequired.add("content");
        writeFileSchema.add("required", writeFileRequired);

        registerTool(new ToolDefinition(
            "write_file",
            "Write file contents",
            "Create or overwrite a file in the project with the specified content.",
            writeFileSchema
        ));

        // Search tool - allows LLM to search across project files
        JsonObject searchSchema = new JsonObject();
        searchSchema.addProperty("type", "object");
        JsonObject searchProps = new JsonObject();
        searchProps.addProperty("query", "string");
        searchSchema.add("properties", searchProps);
        JsonArray searchRequired = new JsonArray();
        searchRequired.add("query");
        searchSchema.add("required", searchRequired);

        registerTool(new ToolDefinition(
            "search_project",
            "Search project files",
            "Search for text patterns across all files in the project.",
            searchSchema
        ));

        // Terminal tool - allows LLM to execute commands
        JsonObject terminalSchema = new JsonObject();
        terminalSchema.addProperty("type", "object");
        JsonObject terminalProps = new JsonObject();
        terminalProps.addProperty("command", "string");
        terminalSchema.add("properties", terminalProps);
        JsonArray terminalRequired = new JsonArray();
        terminalRequired.add("command");
        terminalSchema.add("required", terminalRequired);

        registerTool(new ToolDefinition(
            "execute_command",
            "Execute shell command",
            "Execute a shell command in the project's working directory.",
            terminalSchema
        ));
    }

    /**
     * Get all registered tools as function calling format.
     * Returns list of tool definitions compatible with OpenAI function calling API.
     */
    @NotNull
    public List<java.util.Map<String, Object>> getToolsForFunctionCalling() {
        return tools.values().stream()
                .filter(ToolDefinition::isRegistered)
                .map(ToolDefinition::toFunctionCallingMap)
                .toList();
    }

    /**
     * Get all registered tools as JSON strings for webview.
     */
    @NotNull
    public List<String> getTools() {
        return tools.values().stream()
                .filter(ToolDefinition::isRegistered)
                .map(ToolDefinition::toJson)
                .toList();
    }

    /**
     * Invoke a registered tool with the given parameters.
     * Mirrors invokeTool() from MainThreadLanguageModelToolsShape.kt.
     */
    @NotNull
    public ToolInvocationResult invokeTool(@NotNull String toolId, 
                                            @Nullable JsonObject params) {
        long startTime = System.currentTimeMillis();

        LOG.debug("Invoking tool: " + toolId);

        // Validate tool exists and is registered
        ToolDefinition tool = tools.get(toolId);
        if (tool == null) {
            return ToolInvocationResult.failure(toolId, "Tool with ID '" + toolId + "' not found");
        }

        if (!tool.isRegistered()) {
            return ToolInvocationResult.failure(toolId, "Tool '" + toolId + "' is not registered");
        }

        try {
            // Route to appropriate handler based on tool ID
            ToolInvocationResult result;
            
            switch (toolId) {
                case "read_file":
                    result = handleReadFile(params);
                    break;
                case "write_file":
                    result = handleWriteFile(params);
                    break;
                case "search_project":
                    result = handleSearchProject(params);
                    break;
                case "execute_command":
                    result = handleExecuteCommand(params);
                    break;
                default:
                    // Generic handler for custom tools
                    result = invokeCustomTool(tool, params);
                    break;
            }

            long executionTime = System.currentTimeMillis() - startTime;
            LOG.debug("Tool '" + toolId + "' completed in " + executionTime + "ms");
            
            return result;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            LOG.error("Error invoking tool '" + toolId + "'", e);
            return ToolInvocationResult.failure(toolId, "Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * Handle read_file tool invocation.
     */
    @NotNull
    private ToolInvocationResult handleReadFile(@Nullable JsonObject params) {
        if (params == null || !params.has("path")) {
            return ToolInvocationResult.failure("read_file", "Missing required parameter: path");
        }

        String filePath = params.get("path").getAsString();
        
        // Use IntelliJ's VFS to read the file
        com.intellij.openapi.vfs.VirtualFile virtualFile = 
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath);
        
        if (virtualFile == null) {
            return ToolInvocationResult.failure("read_file", "File not found: " + filePath);
        }

        try {
            byte[] contents = virtualFile.contentsToByteArray();
            String content = new String(contents, java.nio.charset.StandardCharsets.UTF_8);
            
            LOG.debug("[Tool] read_file: Read " + content.length() + " bytes from " + filePath);
            return ToolInvocationResult.successWithData("read_file", content);
            
        } catch (Exception e) {
            return ToolInvocationResult.failure("read_file", "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Handle write_file tool invocation.
     */
    @NotNull
    private ToolInvocationResult handleWriteFile(@Nullable JsonObject params) {
        if (params == null || !params.has("path") || !params.has("content")) {
            return ToolInvocationResult.failure("write_file", "Missing required parameters: path, content");
        }

        String filePath = params.get("path").getAsString();
        String content = params.get("content").getAsString();

        // Use IntelliJ's VFS to write the file
        com.intellij.openapi.vfs.VirtualFile virtualFile = 
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath);
        
        if (virtualFile == null) {
            return ToolInvocationResult.failure("write_file", "File not found: " + filePath);
        }

        try {
            com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction(() -> {
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                        .getDocument(virtualFile).setText(content);
                com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                        .saveDocument(com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
                                .getDocument(virtualFile));
            });

            LOG.debug("[Tool] write_file: Wrote " + content.length() + " bytes to " + filePath);
            return ToolInvocationResult.successWithData("write_file", 
                    "Successfully wrote " + content.length() + " bytes to " + filePath);
            
        } catch (Exception e) {
            return ToolInvocationResult.failure("write_file", "Failed to write file: " + e.getMessage());
        }
    }

    /**
     * Handle search_project tool invocation.
     */
    @NotNull
    private ToolInvocationResult handleSearchProject(@Nullable JsonObject params) {
        if (params == null || !params.has("query")) {
            return ToolInvocationResult.failure("search_project", "Missing required parameter: query");
        }

        String query = params.get("query").getAsString();

        // Delegate to SearchService
        se.tcmt.aurora.search.SearchService searchService = 
                project.getService(se.tcmt.aurora.search.SearchService.class);
        
        if (searchService == null) {
            return ToolInvocationResult.failure("search_project", "Search service not available");
        }

        try {
            // Start a search session
            se.tcmt.aurora.search.SearchSession session = 
                    searchService.startSearch(query, false, false);
            
            // Wait for completion (with timeout)
            int pollCount = 0;
            while (!session.isCompleted() && pollCount < 50) {
                Thread.sleep(100);
                pollCount++;
            }

            List<se.tcmt.aurora.search.SearchResult> results = 
                    searchService.getResults(session.getSessionId());

            if (results != null && !results.isEmpty()) {
                // Format results as string
                StringBuilder sb = new StringBuilder();
                sb.append("Found ").append(results.size()).append(" matches:\n\n");
                
                for (se.tcmt.aurora.search.SearchResult result : results) {
                    sb.append(result.getFilePath());
                    sb.append(":").append(result.getLineNumber());
                    sb.append(" | ");
                    sb.append(result.getMatchedText());
                    sb.append("\n");
                }

                LOG.debug("[Tool] search_project: Found " + results.size() + " matches for '" + query + "'");
                return ToolInvocationResult.successWithData("search_project", sb.toString());
            } else {
                return ToolInvocationResult.success("search_project", 
                        "No matches found for: " + query);
            }

        } catch (Exception e) {
            return ToolInvocationResult.failure("search_project", "Search failed: " + e.getMessage());
        }
    }

    /**
     * Handle execute_command tool invocation.
     */
    @NotNull
    private ToolInvocationResult handleExecuteCommand(@Nullable JsonObject params) {
        if (params == null || !params.has("command")) {
            return ToolInvocationResult.failure("execute_command", "Missing required parameter: command");
        }

        String command = params.get("command").getAsString();

        // Delegate to TerminalService
        se.tcmt.aurora.terminal.TerminalManager terminalManager = 
                project.getService(se.tcmt.aurora.terminal.TerminalManager.class);
        
        if (terminalManager == null) {
            return ToolInvocationResult.failure("execute_command", "Terminal service not available");
        }

        try {
            // Send command to terminal for execution
            terminalManager.sendTextToFirst(command, true);
            
            LOG.debug("[Tool] execute_command: Sent command '" + command + "' to terminal");
            return ToolInvocationResult.success("execute_command", 
                    "Command executed in terminal: " + command);
            
        } catch (Exception e) {
            return ToolInvocationResult.failure("execute_command", "Failed to execute command: " + e.getMessage());
        }
    }

    /**
     * Handle custom tool invocation.
     */
    @NotNull
    private ToolInvocationResult invokeCustomTool(@NotNull ToolDefinition tool, 
                                                    @Nullable JsonObject params) {
        LOG.debug("[Tool] Invoking custom tool: " + tool.getName());
        
        // For now, return a generic success message
        // In the future, this could be extended to support plugin-specific tools
        String resultMessage = "Custom tool '" + tool.getName() + "' invoked with parameters";
        
        if (params != null && !params.isJsonNull()) {
            resultMessage += ": " + params.toString();
        }

        return ToolInvocationResult.success(tool.getId(), resultMessage);
    }

    /**
     * Register a new tool.
     * Mirrors registerTool() from MainThreadLanguageModelToolsShape.kt.
     */
    public void registerTool(@NotNull ToolDefinition tool) {
        tools.put(tool.getId(), tool);
        LOG.debug("Registered tool: " + tool.getName() + " (" + tool.getId() + ")");
    }

    /**
     * Register a new tool by ID (for simple cases).
     */
    public void registerTool(@NotNull String id) {
        ToolDefinition tool = new ToolDefinition(id, id, "Custom tool: " + id, null);
        registerTool(tool);
    }

    /**
     * Unregister a tool.
     * Mirrors unregisterTool() from MainThreadLanguageModelToolsShape.kt.
     */
    public void unregisterTool(@NotNull String name) {
        ToolDefinition tool = tools.get(name);
        if (tool != null) {
            tool.setRegistered(false);
            LOG.debug("Unregistered tool: " + name);
        } else {
            LOG.warn("Attempting to unregister non-existent tool: " + name);
        }
    }

    /**
     * Calculate token count for a given input.
     * Mirrors countTokensForInvocation() from MainThreadLanguageModelToolsShape.kt.
     */
    public int countTokens(@NotNull String input) {
        // Simple token estimation: ~4 characters per token (common heuristic)
        return Math.max(1, input.length() / 4 + 1);
    }

    /**
     * Get the number of registered tools.
     */
    public int getToolCount() {
        return (int) tools.values().stream()
                .filter(ToolDefinition::isRegistered)
                .count();
    }

    /**
     * Check if a tool is registered.
     */
    public boolean isToolRegistered(@NotNull String toolId) {
        ToolDefinition tool = tools.get(toolId);
        return tool != null && tool.isRegistered();
    }

    /**
     * Dispose resources.
     */
    public void dispose() {
        LOG.debug("Disposing ToolService with " + tools.size() + " tools");
        tools.clear();
    }
}
