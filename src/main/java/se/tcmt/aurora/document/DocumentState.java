// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.document;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a synchronized document state for AI context tracking.
 * Mirrors Roo-Code's DocumentSync protocol for VS Code extension host compatibility.
 */
public class DocumentState {
    
    private static final Logger logger = Logger.getInstance(DocumentState.class);
    
    // Document identifiers
    private final String uri;
    private final String languageId;
    
    // Content tracking (mutable)
    private String content;
    private int version;
    private long lastModified;
    
    // State management
    private volatile boolean isDirty = false;
    private volatile boolean isClosed = false;
    
    public DocumentState(
            @NotNull String uri,
            @NotNull String languageId,
            int initialVersion,
            @NotNull String content) {
        this.uri = uri;
        this.languageId = languageId;
        this.version = initialVersion;
        this.content = content;
        this.lastModified = System.currentTimeMillis();
    }
    
    /**
     * Update document state with new content.
     */
    public void updateContent(@NotNull String newContent, int newVersion) {
        if (isClosed) {
            logger.warn("Cannot update closed document: " + uri);
            return;
        }
        
        this.content = newContent;
        this.version = newVersion;
        this.lastModified = System.currentTimeMillis();
        this.isDirty = true;
        
        logger.debug("📝 Document updated: " + uri + " (v" + version + ")");
    }
    
    /**
     * Mark document as clean (saved).
     */
    public void markClean() {
        this.isDirty = false;
        logger.debug("✅ Document marked clean: " + uri);
    }
    
    /**
     * Close the document.
     */
    public void close() {
        this.isClosed = true;
        logger.debug("📄 Document closed: " + uri);
    }
    
    // Getters
    
    public @NotNull String getUri() {
        return uri;
    }
    
    public @NotNull String getLanguageId() {
        return languageId;
    }
    
    public int getVersion() {
        return version;
    }
    
    public @NotNull String getContent() {
        return content;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public boolean isDirty() {
        return isDirty;
    }
    
    public boolean isClosed() {
        return isClosed;
    }
}
