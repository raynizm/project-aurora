// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.bulkedit;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * IntelliJ service for applying bulk/workspace edits.
 * Mirrors Roo-Code's MainThreadBulkEditsShape interface and implementation.
 * 
 * Handles:
 * - File operations: create, delete, rename
 * - Text edits: insert/replace text ranges in open files
 */
public final class BulkEditService {

    private static final Logger logger = Logger.getInstance(BulkEditService.class);

    @NotNull private final Project project;

    public BulkEditService(@NotNull Project project) {
        this.project = project;
        logger.debug("BulkEditService created for: " + project.getName());
    }

    /**
     * Apply a workspace edit (file operations + text edits).
     * Returns true if all operations succeeded, false otherwise.
     */
    public boolean applyWorkspaceEdit(@NotNull WorkspaceEdit workspaceEdit) {
        if (workspaceEdit.isEmpty()) {
            logger.debug("Empty workspace edit, nothing to do");
            return true;
        }

        logger.info("[Bulk Edit] Applying " + workspaceEdit.getOperationCount() + " operations");
        
        boolean allSuccess = true;

        // Process file edits first (create/delete/rename)
        for (FileEdit fileEdit : workspaceEdit.getFiles()) {
            if (!applyFileEdit(fileEdit)) {
                allSuccess = false;
            }
        }

        // Then process text edits
        for (TextEdit textEdit : workspaceEdit.getTexts()) {
            if (!applyTextEdit(textEdit)) {
                allSuccess = false;
            }
        }

        logger.info("[Bulk Edit] Complete: " + (allSuccess ? "success" : "partial failure"));
        return allSuccess;
    }

    /**
     * Apply a single file edit operation.
     */
    private boolean applyFileEdit(@NotNull FileEdit fileEdit) {
        try {
            switch (fileEdit.getOperationType()) {
                case RENAME:
                    return renameFile(fileEdit);
                case DELETE:
                    return deleteFile(fileEdit);
                case CREATE:
                    return createFile(fileEdit);
                default:
                    logger.debug("[Bulk Edit] No operation for file edit");
                    return true;
            }
        } catch (Exception e) {
            logger.error("[Bulk Edit] Failed to apply file edit", e);
            return false;
        }
    }

    /**
     * Rename/move a file.
     */
    private boolean renameFile(@NotNull FileEdit fileEdit) {
        String oldPath = fileEdit.getOldResource();
        String newPath = fileEdit.getNewResource();

        if (oldPath == null || newPath == null) {
            logger.warn("[Bulk Edit] Rename missing paths");
            return false;
        }

        File oldFile = new File(oldPath);
        File newFile = new File(newPath);

        try {
            boolean moved = oldFile.renameTo(newFile);
            if (moved) {
                logger.debug("[Bulk Edit] Renamed: " + oldPath + " -> " + newPath);
                
                // Refresh VFS
                refreshVfs(oldFile, newFile);
                return true;
            } else {
                logger.warn("[Bulk Edit] Rename failed (renameTo returned false): " + oldPath);
                return false;
            }
        } catch (Exception e) {
            logger.error("[Bulk Edit] Failed to rename: " + oldPath, e);
            return false;
        }
    }

    /**
     * Delete a file.
     */
    private boolean deleteFile(@NotNull FileEdit fileEdit) {
        String path = fileEdit.getOldResource();
        if (path == null) {
            logger.warn("[Bulk Edit] Delete missing path");
            return false;
        }

        File file = new File(path);
        try {
            boolean deleted = file.delete();
            if (deleted) {
                logger.debug("[Bulk Edit] Deleted: " + path);
                
                // Refresh parent directory VFS
                refreshVfsParent(file.getParentFile());
                return true;
            } else {
                logger.warn("[Bulk Edit] Delete failed: " + path);
                return false;
            }
        } catch (Exception e) {
            logger.error("[Bulk Edit] Failed to delete: " + path, e);
            return false;
        }
    }

    /**
     * Create a new file.
     */
    private boolean createFile(@NotNull FileEdit fileEdit) {
        String newPath = fileEdit.getNewResource();
        if (newPath == null) {
            logger.warn("[Bulk Edit] Create missing path");
            return false;
        }

        File file = new File(newPath);
        File parentDir = file.getParentFile();

        try {
            // Ensure parent directory exists
            if (parentDir != null && !parentDir.exists()) {
                boolean dirsCreated = parentDir.mkdirs();
                if (!dirsCreated) {
                    logger.warn("[Bulk Edit] Failed to create parent directories: " + newPath);
                    return false;
                }
            }

            // Check overwrite/ignoreIfExists options
            FileEdit.Options options = fileEdit.getOptions();
            if (file.exists()) {
                if (options != null && Boolean.TRUE.equals(options.getIgnoreIfExists())) {
                    logger.debug("[Bulk Edit] Ignoring existing file: " + newPath);
                    return true;
                }
                if (options == null || !Boolean.TRUE.equals(options.getOverwrite())) {
                    logger.warn("[Bulk Edit] File already exists and overwrite not allowed: " + newPath);
                    return false;
                }
            }

            // Create file
            boolean created = file.createNewFile();
            if (!created) {
                logger.warn("[Bulk Edit] createNewFile returned false (may exist): " + newPath);
                return false;
            }

            // Write contents if provided
            if (options != null && options.getContents() != null) {
                Files.writeString(file.toPath(), options.getContents(), StandardCharsets.UTF_8);
            }

            logger.debug("[Bulk Edit] Created: " + newPath);
            
            // Refresh VFS
            refreshVfs(file);
            return true;
        } catch (Exception e) {
            logger.error("[Bulk Edit] Failed to create file: " + newPath, e);
            return false;
        }
    }

    /**
     * Apply a text edit to an open file.
     */
    private boolean applyTextEdit(@NotNull TextEdit textEdit) {
        String resource = textEdit.getResource();
        
        // Only support file:// scheme
        if (!resource.startsWith("file:")) {
            logger.warn("[Bulk Edit] Non-file resources not supported: " + resource);
            return false;
        }

        try {
            // Extract file path from URI
            String filePath = extractFilePath(resource);
            
            // Get the document for this file
            Document document = getDocument(filePath);
            if (document == null) {
                logger.debug("[Bulk Edit] No open document for: " + filePath);
                return false;
            }

            TextEdit.Content content = textEdit.getContent();
            TextEdit.Range range = content.getRange();
            String newText = content.getText();

            // Validate range
            if (!range.isValid()) {
                logger.warn("[Bulk Edit] Invalid range in text edit");
                return false;
            }

            // Apply the text replacement using WriteCommandAction
            ApplicationManager.getApplication().invokeAndWait(() -> {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        int documentLength = document.getTextLength();

                        // Convert range to offsets
                        int startLineZero = range.getStartLineZeroBased();
                        int endLineZero = Math.min(range.getEndLineZeroBased(), document.getLineCount() - 1);

                        // Calculate character offsets from line/column positions
                        int startCharOffset = calculateOffset(document, startLineZero, range.getStartColumn());
                        int endCharOffset = calculateOffset(document, endLineZero, range.getEndColumn());

                        // Clamp to document bounds
                        startCharOffset = Math.max(0, Math.min(startCharOffset, documentLength));
                        endCharOffset = Math.max(0, Math.min(endCharOffset, documentLength));

                        // Replace the text in the range
                        if (endCharOffset > startCharOffset) {
                            document.replaceString(startCharOffset, endCharOffset, newText);
                            logger.debug("[Bulk Edit] Applied text edit: " + filePath + 
                                    " lines " + startLineZero + "-" + endLineZero + 
                                    " (" + newText.length() + " chars inserted)");
                        } else {
                            // Insert at position (no replacement range)
                            document.insertString(startCharOffset, newText);
                            logger.debug("[Bulk Edit] Inserted text: " + filePath + 
                                    " line " + startLineZero + " (" + newText.length() + " chars)");
                        }
                    } catch (Exception e) {
                        logger.error("[Bulk Edit] Failed to apply text edit", e);
                    }
                });
            });

            return true;
        } catch (Exception e) {
            logger.error("[Bulk Edit] Failed to apply text edit: " + resource, e);
            return false;
        }
    }

    /**
     * Get the Document for a file path.
     */
    @Nullable private Document getDocument(@NotNull String filePath) {
        try {
            FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
            LocalFileSystem vfs = LocalFileSystem.getInstance();
            com.intellij.openapi.vfs.VirtualFile virtualFile = vfs.findFileByPath(filePath);
            if (virtualFile == null) {
                return null;
            }
            return fileDocumentManager.getDocument(virtualFile);
        } catch (Exception e) {
            logger.debug("Could not get document for: " + filePath);
            return null;
        }
    }

    /**
     * Calculate character offset from line and column numbers.
     */
    private int calculateOffset(@NotNull Document document, int line, int column) {
        try {
            // Get the start offset of the line
            int lineStart = document.getLineStartOffset(line);
            int lineEnd = document.getLineEndOffset(line);
            // Add the column offset (clamped to line length)
            String lineText = document.getText(com.intellij.openapi.util.TextRange.create(lineStart, lineEnd));
            int colOffset = Math.min(column, lineText.length());
            return lineStart + colOffset;
        } catch (Exception e) {
            logger.debug("Failed to calculate offset for line " + line + ", column " + column);
            return 0;
        }
    }

    /**
     * Extract file path from a URI string.
     */
    @NotNull private String extractFilePath(@NotNull String uri) {
        if (uri.startsWith("file://")) {
            return uri.substring(7); // Remove "file://" prefix
        } else if (uri.startsWith("file:")) {
            return uri.substring(5); // Remove "file:" prefix
        }
        return uri;
    }

    /**
     * Refresh VFS for the given files.
     */
    private void refreshVfs(@NotNull File... files) {
        try {
            LocalFileSystem vfs = LocalFileSystem.getInstance();
            List<File> fileList = new ArrayList<>();
            for (File f : files) {
                if (f != null && f.exists()) {
                    fileList.add(f);
                }
            }
            if (!fileList.isEmpty()) {
                vfs.refreshIoFiles(fileList);
            }
        } catch (Exception e) {
            logger.debug("VFS refresh failed", e);
        }
    }

    /**
     * Refresh VFS for parent directory of a file.
     */
    private void refreshVfsParent(@Nullable File file) {
        if (file != null && file.exists()) {
            refreshVfs(file.getParentFile());
        }
    }
}
