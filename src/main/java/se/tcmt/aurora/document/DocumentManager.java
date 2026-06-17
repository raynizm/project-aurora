// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.document;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * IntelliJ service for managing document synchronization and workspace edits.
 * Registered as a project-scoped service via @Service annotation.
 * Mirrors Roo-Code's DocumentManager registration pattern.
 */
@Service(Service.Level.PROJECT)
public final class DocumentManager {
    
    private static final Logger logger = Logger.getInstance(DocumentManager.class);
    
    private final Project project;
    private final DocumentSyncService documentSyncService;
    private final WorkspaceEditService workspaceEditService;
    
    public DocumentManager(@NotNull Project project) {
        this.project = project;
        this.documentSyncService = new DocumentSyncService(project);
        this.workspaceEditService = new WorkspaceEditService(project);
        
        logger.debug("DocumentManager created for: " + project.getName());
    }
    
    /**
     * Get the document sync service.
     */
    public @NotNull DocumentSyncService getDocumentSync() {
        return documentSyncService;
    }
    
    /**
     * Get the workspace edit service.
     */
    public @NotNull WorkspaceEditService getWorkspaceEdits() {
        return workspaceEditService;
    }
    
    /**
     * Apply a text replacement in the current editor.
     */
    public boolean applyReplacement(
            @NotNull String newText,
            int startLine,
            int startColumn,
            int endLine,
            int endColumn) {
        return workspaceEditService.applyReplacement(newText, startLine, startColumn, endLine, endColumn);
    }
    
    /**
     * Apply full file content replacement.
     */
    public boolean applyFileContent(@NotNull String filePath, @NotNull String newContent) {
        return workspaceEditService.applyFileContent(filePath, newContent);
    }
    
    /**
     * Create a new file with the given content.
     */
    public boolean createFile(@NotNull String filePath, @NotNull String content) {
        return workspaceEditService.createFile(filePath, content);
    }
    
    /**
     * Delete a file from the workspace.
     */
    public boolean deleteFile(@NotNull String filePath) {
        return workspaceEditService.deleteFile(filePath);
    }
    
    /**
     * Get current document context for AI queries.
     */
    public @NotNull String getCurrentDocumentContext() {
        return documentSyncService.getContextForCurrentDocument();
    }
    
    /**
     * Get all tracked documents.
     */
    public @NotNull java.util.Map<String, DocumentState> getAllDocuments() {
        return documentSyncService.getAllDocuments();
    }
}
