// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.search;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IntelliJ service for searching across project files.
 * Registered as a project-scoped service via @Service annotation.
 * Mirrors Roo-Code's MainThreadSearch registration pattern.
 */
@Service(Service.Level.PROJECT)
public final class SearchService {
    
    private static final Logger logger = Logger.getInstance(SearchService.class);
    
    private final Project project;
    private final ConcurrentHashMap<Integer, SearchSession> activeSessions = new ConcurrentHashMap<>();
    private int nextSessionId = 1;
    
    public SearchService(@NotNull Project project) {
        this.project = project;
        logger.debug("SearchService created for: " + project.getName());
    }
    
    /**
     * Start a new search session.
     */
    public @NotNull SearchSession startSearch(@NotNull String pattern, boolean caseSensitive, boolean useRegex) {
        return startSearch(pattern, caseSensitive, useRegex, 1000);
    }
    
    /**
     * Start a new search session with custom max results.
     */
    public @NotNull SearchSession startSearch(@NotNull String pattern, boolean caseSensitive, boolean useRegex, int maxResults) {
        SearchOptions options = new SearchOptions(pattern, caseSensitive, useRegex, maxResults, null, null, "project");
        return startSearchWithOptions(options);
    }
    
    /**
     * Start a search with full options.
     */
    public @NotNull SearchSession startSearchWithOptions(@NotNull SearchOptions options) {
        int sessionId = nextSessionId++;
        SearchSession session = new SearchSession(sessionId, options);
        activeSessions.put(sessionId, session);
        
        // Run search in background thread
        new Thread(() -> executeSearch(session)).start();
        
        return session;
    }
    
    /**
     * Execute the actual search across project files.
     */
    private void executeSearch(@NotNull SearchSession session) {
        try {
            logger.debug("Starting search #" + session.getSessionId() + ": " + session.getOptions().getPattern());
            
            // Get content roots from project and iterate over them
            ContentIterator contentIterator = new ContentIterator() {
                @Override
                public boolean processFile(@NotNull VirtualFile fileOrDir) {
                    if (!fileOrDir.isDirectory()) {
                        if (isSearchableFile(fileOrDir, session.getOptions())) {
                            searchInFile(session, fileOrDir);
                        }
                    }
                    return session.shouldContinue();
                }
            };
            
            // Get content roots from project
            com.intellij.openapi.roots.ProjectRootManager rootManager = 
                    com.intellij.openapi.roots.ProjectRootManager.getInstance(project);
            for (VirtualFile contentRoot : rootManager.getContentRoots()) {
                if (!session.shouldContinue()) break;
                iterateDirectory(contentRoot, session.getOptions(), session, contentIterator);
            }
            
            session.complete();
        } catch (Exception e) {
            logger.error("Search failed", e);
            session.cancel();
        } finally {
            activeSessions.remove(session.getSessionId());
        }
    }
    
    /**
     * Recursively iterate over a directory.
     */
    private void iterateDirectory(@NotNull VirtualFile dir, 
                                   @NotNull SearchOptions options,
                                   @NotNull SearchSession session,
                                   @NotNull ContentIterator iterator) {
        if (!dir.isDirectory()) return;
        
        for (VirtualFile child : dir.getChildren()) {
            if (!iterator.processFile(child)) break;
            if (child.isDirectory() && session.shouldContinue()) {
                iterateDirectory(child, options, session, iterator);
            }
        }
    }
    
    /**
     * Check if a file is searchable based on options.
     */
    private boolean isSearchableFile(@NotNull VirtualFile file, @NotNull SearchOptions options) {
        // Skip binary files (check by extension)
        String extension = file.getExtension();
        if (!isTextExtension(extension)) {
            return false;
        }
        
        // Check include patterns
        String[] includes = options.getIncludePatterns();
        if (includes != null && includes.length > 0) {
            boolean matches = false;
            for (String pattern : includes) {
                if (file.getName().matches(pattern.replace("*", ".*"))) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return false;
            }
        }
        
        // Check exclude patterns
        String[] excludes = options.getExcludePatterns();
        if (excludes != null && excludes.length > 0) {
            for (String pattern : excludes) {
                if (file.getName().matches(pattern.replace("*", ".*"))) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Check if an extension indicates a text file.
     */
    private boolean isTextExtension(@Nullable String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        
        switch (extension.toLowerCase()) {
            case "java":
            case "kt":
            case "kts":
            case "py":
            case "js":
            case "ts":
            case "html":
            case "css":
            case "xml":
            case "yaml":
            case "yml":
            case "json":
            case "md":
            case "txt":
            case "sh":
            case "bash":
            case "gradle":
            case "properties":
            case "ini":
            case "cfg":
            case "conf":
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Search within a single file.
     */
    private void searchInFile(@NotNull SearchSession session, @NotNull VirtualFile file) {
        try {
            Path path = Path.of(file.getPath());
            
            if (!Files.exists(path)) {
                return;
            }
            
            String patternStr = session.getOptions().getPattern();
            Pattern pattern;
            
            try {
                if (session.getOptions().isUseRegex()) {
                    int flags = session.getOptions().isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(patternStr, flags);
                } else {
                    // Escape special regex characters for literal search
                    String escaped = Pattern.quote(patternStr);
                    int flags = session.getOptions().isCaseSensitive() ? 0 : Pattern.CASE_INSENSITIVE;
                    pattern = Pattern.compile(escaped, flags);
                }
            } catch (Exception e) {
                logger.warn("Invalid regex pattern: " + patternStr);
                return;
            }
            
            // Read file line by line
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                int lineNumber = 0;
                
                while ((line = reader.readLine()) != null && session.shouldContinue()) {
                    lineNumber++;
                    
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        // Calculate context
                        int start = Math.max(0, matcher.start() - 30);
                        int end = Math.min(line.length(), matcher.end() + 30);
                        
                        String before = line.substring(start, matcher.start());
                        String after = line.substring(matcher.end(), end);
                        
                        SearchResult result = new SearchResult(
                                file.getPath(),
                                lineNumber,
                                matcher.start() + 1, // 1-indexed column
                                matcher.group(),
                                before,
                                after,
                                matcher.start()
                        );
                        
                        session.addResult(result);
                    }
                }
            }
            
            session.incrementFilesScanned();
            
        } catch (IOException e) {
            logger.debug("Could not read file: " + file.getPath());
        }
    }
    
    /**
     * Cancel a search session.
     */
    public void cancelSession(int sessionId) {
        SearchSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.cancel();
            logger.debug("Cancelled search session #" + sessionId);
        }
    }
    
    /**
     * Get results from a completed search session.
     */
    public @Nullable List<SearchResult> getResults(int sessionId) {
        SearchSession session = activeSessions.get(sessionId);
        if (session != null && session.isCompleted()) {
            return session.getResults();
        }
        return null;
    }
    
    /**
     * Get all active sessions.
     */
    public @NotNull ConcurrentHashMap<Integer, SearchSession> getActiveSessions() {
        return activeSessions;
    }
}
