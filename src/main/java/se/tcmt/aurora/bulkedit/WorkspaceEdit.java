// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.bulkedit;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for a workspace edit operation.
 * Mirrors Roo-Code's WorkspaceEdit class from WorkspaceEdit.kt.
 * 
 * Parses JSON with an "edits" array containing either:
 * - {"textEdit": {...}} — text edits to apply in open files
 * - {"fileEdit": {...}} — file operations (create/delete/rename)
 */
public final class WorkspaceEdit {

    @NotNull private final List<FileEdit> files;
    @NotNull private final List<TextEdit> texts;

    public WorkspaceEdit() {
        this.files = new ArrayList<>();
        this.texts = new ArrayList<>();
    }

    @NotNull public List<FileEdit> getFiles() { return files; }
    @NotNull public List<TextEdit> getTexts() { return texts; }

    /**
     * Parse a WorkspaceEdit from JSON string.
     * Expected format: {"edits": [{"textEdit": {...}}, {"fileEdit": {...}}]}
     */
    @Nullable public static WorkspaceEdit fromJson(@NotNull String json) {
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(json, JsonObject.class);
            
            JsonElement editsElement = root.get("edits");
            if (editsElement == null || !editsElement.isJsonArray()) {
                return null;
            }

            WorkspaceEdit edit = new WorkspaceEdit();
            JsonArray edits = editsElement.getAsJsonArray();

            for (JsonElement element : edits) {
                JsonObject editObj = element.getAsJsonObject();
                
                if (editObj.has("textEdit")) {
                    // Parse text edit
                    TextEdit textEdit = parseTextEdit(editObj.get("textEdit").getAsJsonObject());
                    if (textEdit != null) {
                        edit.texts.add(textEdit);
                    }
                } else if (editObj.has("fileEdit")) {
                    // Parse file edit
                    FileEdit fileEdit = parseFileEdit(editObj.get("fileEdit").getAsJsonObject());
                    if (fileEdit != null) {
                        edit.files.add(fileEdit);
                    }
                }
            }

            return edit;
        } catch (Exception e) {
            // Log error but don't throw — let caller handle gracefully
            System.err.println("Failed to parse WorkspaceEdit: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse a single text edit from JSON.
     */
    @Nullable private static TextEdit parseTextEdit(@NotNull JsonObject json) {
        try {
            String resource = json.has("resource") ? json.get("resource").getAsString() : "";
            
            // Parse content (range + text)
            JsonObject contentJson = json.getAsJsonObject("content");
            if (contentJson == null) return null;

            TextEdit.Range range = parseRange(contentJson.getAsJsonObject("range"));
            String text = contentJson.has("text") ? contentJson.get("text").getAsString() : "";

            // Parse optional metadata
            FileEdit.Metadata metadata = null;
            if (json.has("metadata")) {
                JsonObject metaJson = json.getAsJsonObject("metadata");
                boolean isRefactoring = metaJson.has("isRefactoring") && 
                        metaJson.get("isRefactoring").getAsBoolean();
                metadata = new FileEdit.Metadata(isRefactoring);
            }

            return new TextEdit(resource, new TextEdit.Content(range, text), metadata);
        } catch (Exception e) {
            System.err.println("Failed to parse TextEdit: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse a range from JSON.
     */
    @Nullable private static TextEdit.Range parseRange(@NotNull JsonObject json) {
        try {
            int startLine = json.has("startLine") ? json.get("startLine").getAsInt() : 1;
            int startColumn = json.has("startColumn") ? json.get("startColumn").getAsInt() : 0;
            int endLine = json.has("endLine") ? json.get("endLine").getAsInt() : 1;
            int endColumn = json.has("endColumn") ? json.get("endColumn").getAsInt() : 0;

            return new TextEdit.Range(startLine, startColumn, endLine, endColumn);
        } catch (Exception e) {
            System.err.println("Failed to parse Range: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse a file edit from JSON.
     */
    @Nullable private static FileEdit parseFileEdit(@NotNull JsonObject json) {
        try {
            String oldResource = json.has("oldResource") ? json.get("oldResource").getAsString() : null;
            String newResource = json.has("newResource") ? json.get("newResource").getAsString() : null;

            // Parse options
            FileEdit.Options options = null;
            if (json.has("options")) {
                JsonObject optionsJson = json.getAsJsonObject("options");
                Boolean overwrite = optionsJson.has("overwrite") && 
                        optionsJson.get("overwrite").getAsBoolean();
                Boolean ignoreIfExists = optionsJson.has("ignoreIfExists") && 
                        optionsJson.get("ignoreIfExists").getAsBoolean();
                String contents = optionsJson.has("contents") ? 
                        optionsJson.get("contents").getAsString() : null;

                options = new FileEdit.Options(overwrite, ignoreIfExists, contents);
            }

            // Parse metadata
            FileEdit.Metadata metadata = null;
            if (json.has("metadata")) {
                JsonObject metaJson = json.getAsJsonObject("metadata");
                boolean isRefactoring = metaJson.has("isRefactoring") && 
                        metaJson.get("isRefactoring").getAsBoolean();
                metadata = new FileEdit.Metadata(isRefactoring);
            }

            return new FileEdit(oldResource, newResource, options, metadata);
        } catch (Exception e) {
            System.err.println("Failed to parse FileEdit: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if this workspace edit has any operations.
     */
    public boolean isEmpty() {
        return files.isEmpty() && texts.isEmpty();
    }

    /**
     * Get total number of operations.
     */
    public int getOperationCount() {
        return files.size() + texts.size();
    }
}
