// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.bulkedit;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a file-level edit operation (create, delete, rename).
 * Mirrors Roo-Code's FileEdit data class from WorkspaceEdit.kt.
 */
public final class FileEdit {

    @Nullable private final String oldResource;
    @Nullable private final String newResource;
    @Nullable private final Options options;
    @Nullable private final Metadata metadata;

    public FileEdit(@Nullable String oldResource, @Nullable String newResource, 
                    @Nullable Options options, @Nullable Metadata metadata) {
        this.oldResource = oldResource;
        this.newResource = newResource;
        this.options = options;
        this.metadata = metadata;
    }

    @Nullable public String getOldResource() { return oldResource; }
    @Nullable public String getNewResource() { return newResource; }
    @Nullable public Options getOptions() { return options; }
    @Nullable public Metadata getMetadata() { return metadata; }

    /**
     * Determines the type of file operation.
     */
    public OperationType getOperationType() {
        if (oldResource != null && newResource != null) {
            return OperationType.RENAME;
        } else if (oldResource != null) {
            return OperationType.DELETE;
        } else if (newResource != null) {
            return OperationType.CREATE;
        }
        return OperationType.NONE;
    }

    public enum OperationType { RENAME, DELETE, CREATE, NONE }

    /**
     * Options for file operations.
     */
    public static final class Options {
        @Nullable private final Boolean overwrite;
        @Nullable private final Boolean ignoreIfExists;
        @Nullable private final String contents;

        public Options(@Nullable Boolean overwrite, @Nullable Boolean ignoreIfExists, 
                       @Nullable String contents) {
            this.overwrite = overwrite;
            this.ignoreIfExists = ignoreIfExists;
            this.contents = contents;
        }

        @Nullable public Boolean getOverwrite() { return overwrite; }
        @Nullable public Boolean getIgnoreIfExists() { return ignoreIfExists; }
        @Nullable public String getContents() { return contents; }
    }

    /**
     * Metadata for file operations.
     */
    public static final class Metadata {
        private final boolean isRefactoring;

        public Metadata(boolean isRefactoring) {
            this.isRefactoring = isRefactoring;
        }

        public boolean isIsRefactoring() { return isRefactoring; }
    }
}
