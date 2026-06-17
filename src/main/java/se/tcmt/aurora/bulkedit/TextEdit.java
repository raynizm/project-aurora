// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.bulkedit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a text edit operation on a specific file resource.
 * Mirrors Roo-Code's TextEdit data class from WorkspaceEdit.kt.
 */
public final class TextEdit {

    @NotNull private final String resource;
    @NotNull private final Content content;
    @Nullable private final FileEdit.Metadata metadata;

    public TextEdit(@NotNull String resource, @NotNull Content content, 
                    @Nullable FileEdit.Metadata metadata) {
        this.resource = resource;
        this.content = content;
        this.metadata = metadata;
    }

    @NotNull public String getResource() { return resource; }
    @NotNull public Content getContent() { return content; }
    @Nullable public FileEdit.Metadata getMetadata() { return metadata; }

    /**
     * The text content to insert/replace, with range information.
     */
    public static final class Content {
        private final Range range;
        @NotNull private final String text;

        public Content(@NotNull Range range, @NotNull String text) {
            this.range = range;
            this.text = text;
        }

        @NotNull public Range getRange() { return range; }
        @NotNull public String getText() { return text; }
    }

    /**
     * A range within a document, specified by start and end positions.
     */
    public static final class Range {
        private final int startLine;
        private final int startColumn;
        private final int endLine;
        private final int endColumn;

        public Range(int startLine, int startColumn, int endLine, int endColumn) {
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
        }

        public int getStartLine() { return startLine; }
        public int getStartColumn() { return startColumn; }
        public int getEndLine() { return endLine; }
        public int getEndColumn() { return endColumn; }

        /**
         * Convert to 0-indexed line numbers (IntelliJ uses 0-indexed lines).
         */
        public int getStartLineZeroBased() { return Math.max(0, startLine - 1); }
        public int getEndLineZeroBased() { return Math.min(endLine - 1, Integer.MAX_VALUE); }

        /**
         * Check if this range is valid.
         */
        public boolean isValid() {
            return startLine >= 1 && endLine >= startLine;
        }
    }
}
