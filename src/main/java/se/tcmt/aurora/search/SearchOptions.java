// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.search;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration options for file search queries.
 */
public class SearchOptions {
    
    private final @NotNull String pattern;
    private final boolean caseSensitive;
    private final boolean useRegex;
    private final int maxResults;
    private final @Nullable String[] includePatterns;
    private final @Nullable String[] excludePatterns;
    private final @Nullable String searchScope; // "project", "directory", or path
    
    public SearchOptions(
            @NotNull String pattern,
            boolean caseSensitive,
            boolean useRegex,
            int maxResults,
            @Nullable String[] includePatterns,
            @Nullable String[] excludePatterns,
            @Nullable String searchScope) {
        this.pattern = pattern;
        this.caseSensitive = caseSensitive;
        this.useRegex = useRegex;
        this.maxResults = Math.max(100, maxResults); // minimum 100 results
        this.includePatterns = includePatterns;
        this.excludePatterns = excludePatterns;
        this.searchScope = searchScope != null ? searchScope : "project";
    }
    
    /**
     * Create default options (case-insensitive, no regex).
     */
    public static @NotNull SearchOptions defaults(@NotNull String pattern) {
        return new SearchOptions(pattern, false, false, 1000, null, null, "project");
    }
    
    /**
     * Create case-sensitive options.
     */
    public static @NotNull SearchOptions caseSensitive(@NotNull String pattern) {
        return new SearchOptions(pattern, true, false, 1000, null, null, "project");
    }
    
    /**
     * Create regex-based options.
     */
    public static @NotNull SearchOptions regex(@NotNull String pattern) {
        return new SearchOptions(pattern, false, true, 1000, null, null, "project");
    }
    
    // Getters
    
    public @NotNull String getPattern() {
        return pattern;
    }
    
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    
    public boolean isUseRegex() {
        return useRegex;
    }
    
    public int getMaxResults() {
        return maxResults;
    }
    
    public @Nullable String[] getIncludePatterns() {
        return includePatterns;
    }
    
    public @Nullable String[] getExcludePatterns() {
        return excludePatterns;
    }
    
    public @NotNull String getSearchScope() {
        return searchScope;
    }
}
