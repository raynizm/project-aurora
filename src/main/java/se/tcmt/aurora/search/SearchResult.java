// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.search;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single search result match within a file.
 */
public class SearchResult {
    
    private final String filePath;
    private final int lineNumber;
    private final int columnNumber;
    private final String matchedText;
    private final String contextBefore;
    private final String contextAfter;
    private final int matchOffset; // offset within the line where the match starts
    
    public SearchResult(
            @NotNull String filePath,
            int lineNumber,
            int columnNumber,
            @NotNull String matchedText,
            @Nullable String contextBefore,
            @Nullable String contextAfter,
            int matchOffset) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.matchedText = matchedText;
        this.contextBefore = contextBefore != null ? contextBefore : "";
        this.contextAfter = contextAfter != null ? contextAfter : "";
        this.matchOffset = matchOffset;
    }
    
    public @NotNull String getFilePath() {
        return filePath;
    }
    
    public int getLineNumber() {
        return lineNumber;
    }
    
    public int getColumnNumber() {
        return columnNumber;
    }
    
    public @NotNull String getMatchedText() {
        return matchedText;
    }
    
    public @NotNull String getContextBefore() {
        return contextBefore;
    }
    
    public @NotNull String getContextAfter() {
        return contextAfter;
    }
    
    public int getMatchOffset() {
        return matchOffset;
    }
    
    /**
     * Get the full line content with highlighting markers.
     */
    public @NotNull String getHighlightedLine() {
        StringBuilder sb = new StringBuilder();
        
        // Add context before (if any)
        if (!contextBefore.isEmpty()) {
            sb.append(contextBefore);
        }
        
        // Highlight the matched text
        sb.append(">>").append(matchedText).append("<<");
        
        // Add context after (if any)
        if (!contextAfter.isEmpty()) {
            sb.append(contextAfter);
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "SearchResult{" +
                "file='" + filePath + '\'' +
                ", line=" + lineNumber +
                ", col=" + columnNumber +
                ", match='" + matchedText + '\'' +
                '}';
    }
}
