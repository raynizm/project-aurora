// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.tool;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the result of a tool invocation.
 * Mirrors the return format from Roo-Code's invokeTool() method.
 */
public class ToolInvocationResult {

    private final String toolId;
    private final boolean success;
    @Nullable
    private final String message;
    @Nullable
    private final Object data;
    private final long executionTimeMs;

    public ToolInvocationResult(@NotNull String toolId, boolean success, 
                                 @Nullable String message, @Nullable Object data,
                                 long executionTimeMs) {
        this.toolId = toolId;
        this.success = success;
        this.message = message;
        this.data = data;
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Create a successful result.
     */
    @NotNull
    public static ToolInvocationResult success(@NotNull String toolId, 
                                                @Nullable String message) {
        return new ToolInvocationResult(toolId, true, message, null, 0);
    }

    /**
     * Create a successful result with data.
     */
    @NotNull
    public static ToolInvocationResult successWithData(@NotNull String toolId, 
                                                        @Nullable Object data) {
        return new ToolInvocationResult(toolId, true, "Tool executed successfully", data, 0);
    }

    /**
     * Create a failed result.
     */
    @NotNull
    public static ToolInvocationResult failure(@NotNull String toolId, 
                                                @NotNull String errorMessage) {
        return new ToolInvocationResult(toolId, false, errorMessage, null, 0);
    }

    /**
     * Get the tool ID that was invoked.
     */
    @NotNull
    public String getToolId() {
        return toolId;
    }

    /**
     * Check if the invocation was successful.
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Get the result message.
     */
    @Nullable
    public String getMessage() {
        return message;
    }

    /**
     * Get the result data (can be any type).
     */
    @Nullable
    public Object getData() {
        return data;
    }

    /**
     * Get execution time in milliseconds.
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Convert to JSON string for webview communication.
     */
    @NotNull
    public String toJson() {
        Gson gson = new Gson();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("id", toolId);
        result.put("success", success);
        
        if (message != null) {
            result.put("message", message);
        }
        
        if (data != null) {
            // Serialize data to JSON string for safe transmission
            result.put("data", gson.toJsonTree(data));
        }
        
        result.put("executionTimeMs", executionTimeMs);
        
        return gson.toJson(result);
    }

    @Override
    public String toString() {
        return "ToolInvocationResult{toolId='" + toolId + 
               "', success=" + success + 
               ", message='" + message + "'" +
               ", executionTimeMs=" + executionTimeMs + "}";
    }
}
