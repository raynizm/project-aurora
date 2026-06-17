// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.tool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a registered language model tool with its schema and metadata.
 * Mirrors the ToolInfo structure from Roo-Code's MainThreadLanguageModelToolsShape.kt.
 */
public class ToolDefinition {

    private final String id;
    private final String name;
    private final String description;
    private final JsonObject parametersSchema; // JSON Schema format
    private volatile boolean registered;

    public ToolDefinition(@NotNull String id, @NotNull String name, 
                          @NotNull String description, 
                          @Nullable JsonObject parametersSchema) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema != null ? parametersSchema : new JsonObject();
        this.registered = true;
    }

    /**
     * Get the tool ID (unique identifier).
     */
    @NotNull
    public String getId() {
        return id;
    }

    /**
     * Get the tool name.
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Get the tool description (used for LLM function calling).
     */
    @NotNull
    public String getDescription() {
        return description;
    }

    /**
     * Get the JSON Schema parameters.
     */
    @Nullable
    public JsonObject getParametersSchema() {
        return parametersSchema;
    }

    /**
     * Check if the tool is registered and available for invocation.
     */
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Set registration status.
     */
    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    /**
     * Convert to a Map representation for LLM function calling format.
     */
    @NotNull
    public java.util.Map<String, Object> toFunctionCallingMap() {
        java.util.Map<String, Object> tool = new java.util.LinkedHashMap<>();
        
        // Function name and description (OpenAI-compatible format)
        java.util.Map<String, String> function = new java.util.LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        
        // Parameters schema
        if (parametersSchema != null && !parametersSchema.isJsonNull()) {
            String schemaJson = parametersSchema.toString();
            function.put("parameters", schemaJson);
        } else {
            function.put("parameters", "{}");
        }
        
        tool.put("type", "function");
        tool.put("function", function);
        
        return tool;
    }

    /**
     * Convert to JSON string for webview communication.
     */
    @NotNull
    public String toJson() {
        Gson gson = new Gson();
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("name", name);
        json.addProperty("description", description);
        if (parametersSchema != null) {
            json.add("parameters", parametersSchema);
        }
        json.addProperty("registered", registered);
        return gson.toJson(json);
    }

    @Override
    public String toString() {
        return "ToolDefinition{id='" + id + "', name='" + name + 
               "', registered=" + registered + "}";
    }
}
