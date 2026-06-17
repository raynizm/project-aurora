// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.document;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages document synchronization between IntelliJ editor and AI context.
 * Mirrors Roo-Code's DocumentSyncService for VS Code extension host compatibility.
 * 
 * Features:
 * - Track open documents with their content and version
 * - Provide current document context for AI queries
 * - Manage document lifecycle (open/close/update)
 */
public class DocumentSyncService implements Disposable {
    
    private static final Logger logger = Logger.getInstance(DocumentSyncService.class);
    
    private final Project project;
    private final Map<String, DocumentState> documents = new ConcurrentHashMap<>();
    
    public DocumentSyncService(@NotNull Project project) {
        this.project = project;
        logger.debug("DocumentSyncService initialized for: " + project.getName());
    }
    
    /**
     * Create a DocumentState from an IntelliJ document.
     */
    private @NotNull DocumentState createDocumentState(@NotNull Document document, @NotNull VirtualFile virtualFile) {
        String uri = toUri(virtualFile);
        String languageId = getLanguageId(virtualFile);
        
        return new DocumentState(uri, languageId, 1, document.getText());
    }
    
    /**
     * Convert a VirtualFile to a URI string.
     */
    private @NotNull String toUri(@NotNull VirtualFile file) {
        String path = file.getPath();
        // Use file:// scheme for consistency with VS Code protocol
        return "file://" + path.replace('\\', '/');
    }
    
    /**
     * Get the language ID from a virtual file.
     */
    private @NotNull String getLanguageId(@NotNull VirtualFile file) {
        String extension = file.getExtension();
        if (extension == null || extension.isEmpty()) {
            return "plaintext";
        }
        
        // Map common extensions to language IDs
        switch (extension.toLowerCase()) {
            case "java": return "java";
            case "kt":
            case "kts": return "kotlin";
            case "py": return "python";
            case "js": return "javascript";
            case "ts": return "typescript";
            case "html": return "html";
            case "css": return "css";
            case "xml": return "xml";
            case "yaml":
            case "yml": return "yaml";
            case "json": return "json";
            case "md": return "markdown";
            case "sh":
            case "bash": return "shellscript";
            default: return extension;
        }
    }
    
    /**
     * Get or create a document state for the given virtual file.
     */
    public @Nullable DocumentState getDocument(@NotNull VirtualFile file) {
        String uri = toUri(file);
        return documents.get(uri);
    }
    
    /**
     * Register a new open document.
     */
    public void registerDocument(@NotNull VirtualFile file, @NotNull Document document) {
        String uri = toUri(file);
        
        ApplicationManager.getApplication().invokeLater(() -> {
            DocumentState state = createDocumentState(document, file);
            documents.put(uri, state);
            logger.debug("✅ Document registered: " + uri);
        });
    }
    
    /**
     * Unregister a closed document.
     */
    public void unregisterDocument(@NotNull VirtualFile file) {
        String uri = toUri(file);
        
        ApplicationManager.getApplication().invokeLater(() -> {
            DocumentState state = documents.remove(uri);
            if (state != null) {
                state.close();
                logger.debug("✅ Document unregistered: " + uri);
            }
        });
    }
    
    /**
     * Update a document's content.
     */
    public void updateDocument(@NotNull VirtualFile file, @NotNull String newContent) {
        String uri = toUri(file);
        
        ApplicationManager.getApplication().invokeLater(() -> {
            DocumentState state = documents.get(uri);
            if (state != null) {
                state.updateContent(newContent, state.getVersion() + 1);
                state.markClean();
            } else {
                // Create new entry if not tracked
                Document document = FileDocumentManager.getInstance().getDocument(file);
                if (document != null) {
                    state = createDocumentState(document, file);
                    documents.put(uri, state);
                }
            }
        });
    }
    
    /**
     * Get all tracked documents.
     */
    public @NotNull Map<String, DocumentState> getAllDocuments() {
        return new ConcurrentHashMap<>(documents);
    }
    
    /**
     * Get the current document from the active editor (if any).
     */
    public @Nullable DocumentState getCurrentDocument() {
        com.intellij.openapi.editor.Editor editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor();
        
        if (editor != null) {
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (file != null) {
                return getDocument(file);
            }
        }
        
        return null;
    }
    
    /**
     * Get document content for AI context.
     */
    public @NotNull String getContextForCurrentDocument() {
        DocumentState state = getCurrentDocument();
        if (state != null) {
            return "File: " + state.getUri() + "\nLanguage: " + state.getLanguageId() + "\n\n" + state.getContent();
        }
        return "";
    }
    
    @Override
    public void dispose() {
        // Close all tracked documents
        for (DocumentState state : documents.values()) {
            state.close();
        }
        documents.clear();
        
        logger.debug("✅ DocumentSyncService disposed");
    }
}
