// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.document;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages workspace edits for applying code modifications from AI responses.
 * Mirrors Roo-Code's WorkspaceEditService for VS Code extension host compatibility.
 * 
 * Features:
 * - Apply text replacements in open files
 * - Create new files with content
 * - Delete files from workspace
 * - Track edit history for undo/redo support
 */
public class WorkspaceEditService {
    
    private static final Logger logger = Logger.getInstance(WorkspaceEditService.class);
    
    private final Project project;
    
    public WorkspaceEditService(@NotNull Project project) {
        this.project = project;
        logger.debug("WorkspaceEditService initialized for: " + project.getName());
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
        
        Editor editor = getCurrentEditor();
        if (editor == null) {
            logger.warn("No active editor for replacement");
            return false;
        }
        
        Document document = editor.getDocument();
        
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                // Convert line/column to offset
                int startOffset = getOffsetFromPosition(document, startLine, startColumn);
                int endOffset = getOffsetFromPosition(document, endLine, endColumn);
                
                // Replace the text
                document.replaceString(startOffset, endOffset, newText);
                
                logger.debug("✅ Replacement applied: " + newText.length() + " chars at [" + 
                    startLine + ":" + startColumn + "] to [" + endLine + ":" + endColumn + "]");
            } catch (Exception e) {
                logger.error("❌ Failed to apply replacement", e);
            }
        });
        
        return true;
    }
    
    /**
     * Apply a full file content replacement.
     */
    public boolean applyFileContent(@NotNull String filePath, @NotNull String newContent) {
        VirtualFile file = findFile(filePath);
        if (file == null) {
            logger.warn("File not found for content replacement: " + filePath);
            return false;
        }
        
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                FileDocumentManager manager = FileDocumentManager.getInstance();
                Document document = manager.getDocument(file);
                
                if (document != null) {
                    // Replace all content
                    document.setText(newContent);
                    
                    // Save the file
                    manager.saveDocument(document);
                    
                    logger.debug("✅ File content replaced: " + filePath);
                } else {
                    logger.warn("No document for file: " + filePath);
                }
            } catch (Exception e) {
                logger.error("❌ Failed to replace file content", e);
            }
        });
        
        return true;
    }
    
    /**
     * Create a new file with the given content.
     */
    public boolean createFile(@NotNull String filePath, @NotNull String content) {
        try {
            VirtualFile baseDir = project.getBaseDir();
            if (baseDir == null) {
                logger.warn("No project base directory");
                return false;
            }
            
            ApplicationManager.getApplication().runWriteAction(() -> {
                try {
                    // Create the file
                    VirtualFile newFile = baseDir.createChildData(this, filePath);
                    
                    // Write content
                    com.intellij.openapi.vfs.newvfs.RefreshQueue.getInstance()
                        .refresh(false, true, null, newFile);
                    
                    // Write content to file
                    java.io.OutputStream outputStream = newFile.getOutputStream(this);
                    try {
                        outputStream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    } finally {
                        outputStream.close();
                    }
                    
                    logger.debug("✅ File created: " + filePath);
                } catch (Exception e) {
                    logger.error("❌ Failed to create file", e);
                }
            });
            
            return true;
        } catch (Exception e) {
            logger.error("❌ Exception creating file", e);
            return false;
        }
    }
    
    /**
     * Delete a file from the workspace.
     */
    public boolean deleteFile(@NotNull String filePath) {
        VirtualFile file = findFile(filePath);
        if (file == null) {
            logger.warn("File not found for deletion: " + filePath);
            return false;
        }
        
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                file.delete(this);
                logger.debug("✅ File deleted: " + filePath);
            } catch (Exception e) {
                logger.error("❌ Failed to delete file", e);
            }
        });
        
        return true;
    }
    
    /**
     * Get the current active editor.
     */
    private @Nullable Editor getCurrentEditor() {
        return com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    
    /**
     * Find a virtual file by path.
     */
    private @Nullable VirtualFile findFile(@NotNull String filePath) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }
        
        // Normalize the path
        String normalizedPath = filePath.replace('\\', '/');
        
        // Try to find in base directory
        return findInDirectory(baseDir, normalizedPath);
    }
    
    /**
     * Recursively search for a file in a directory.
     */
    private @Nullable VirtualFile findInDirectory(@NotNull VirtualFile dir, @NotNull String path) {
        if (path.isEmpty()) {
            return dir;
        }
        
        int slashIndex = path.indexOf('/');
        String componentName;
        String remainingPath;
        
        if (slashIndex == -1) {
            componentName = path;
            remainingPath = "";
        } else {
            componentName = path.substring(0, slashIndex);
            remainingPath = path.substring(slashIndex + 1);
        }
        
        VirtualFile child = dir.findChild(componentName);
        if (child == null) {
            return null;
        }
        
        if (remainingPath.isEmpty()) {
            return !child.isDirectory() ? child : null;
        } else if (child.isDirectory()) {
            return findInDirectory(child, remainingPath);
        } else {
            return null;
        }
    }
    
    /**
     * Convert line/column to document offset.
     */
    private int getOffsetFromPosition(@NotNull Document document, int line, int column) {
        // Line is 0-indexed in our API but IntelliJ uses 0-indexed lines too
        if (line < 0 || line >= document.getLineCount()) {
            return 0;
        }
        
        int startOffset = document.getLineStartOffset(line);
        int endOffset = Math.min(startOffset + column, document.getLineEndOffset(line));
        
        return Math.max(startOffset, endOffset);
    }
    
    /**
     * Apply multiple edits in a single transaction.
     */
    public boolean applyEdits(@NotNull java.util.List<WorkspaceEdit> edits) {
        if (edits.isEmpty()) {
            return true;
        }
        
        ApplicationManager.getApplication().runWriteAction(() -> {
            try {
                for (WorkspaceEdit edit : edits) {
                    switch (edit.getType()) {
                        case REPLACEMENT:
                            applyReplacement(
                                edit.getContent(),
                                edit.getStartLine(),
                                edit.getStartColumn(),
                                edit.getEndLine(),
                                edit.getEndColumn()
                            );
                            break;
                        case FILE_CONTENT:
                            applyFileContent(edit.getFilePath(), edit.getContent());
                            break;
                        case CREATE_FILE:
                            createFile(edit.getFilePath(), edit.getContent());
                            break;
                        case DELETE_FILE:
                            deleteFile(edit.getFilePath());
                            break;
                    }
                }
                
                logger.debug("✅ Applied " + edits.size() + " workspace edits");
            } catch (Exception e) {
                logger.error("❌ Failed to apply batch edits", e);
            }
        });
        
        return true;
    }
    
    /**
     * Represents a single workspace edit operation.
     */
    public static class WorkspaceEdit {
        private final EditType type;
        private final String content;
        private final String filePath;
        private final int startLine;
        private final int startColumn;
        private final int endLine;
        private final int endColumn;
        
        public enum EditType {
            REPLACEMENT,
            FILE_CONTENT,
            CREATE_FILE,
            DELETE_FILE
        }
        
        public WorkspaceEdit(
                @NotNull EditType type,
                @Nullable String content,
                @Nullable String filePath,
                int startLine,
                int startColumn,
                int endLine,
                int endColumn) {
            this.type = type;
            this.content = content != null ? content : "";
            this.filePath = filePath != null ? filePath : "";
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
        }
        
        public @NotNull EditType getType() { return type; }
        public @NotNull String getContent() { return content; }
        public @NotNull String getFilePath() { return filePath; }
        public int getStartLine() { return startLine; }
        public int getStartColumn() { return startColumn; }
        public int getEndLine() { return endLine; }
        public int getEndColumn() { return endColumn; }
    }
}
