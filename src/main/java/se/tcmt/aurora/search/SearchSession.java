// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.search;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks an ongoing search session with progress reporting and cancellation support.
 */
public class SearchSession {
    
    private static final Logger logger = Logger.getInstance(SearchSession.class);
    
    private final int sessionId;
    private final @NotNull SearchOptions options;
    private final List<SearchResult> results = new CopyOnWriteArrayList<>();
    private final AtomicInteger filesScanned = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean completed = false;
    
    // Progress callback (optional)
    @Nullable
    private SearchProgressCallback progressCallback;
    
    public interface SearchProgressCallback {
        void onProgress(int filesScanned, int resultsFound, boolean isCancelled);
    }
    
    public SearchSession(int sessionId, @NotNull SearchOptions options) {
        this.sessionId = sessionId;
        this.options = options;
        logger.debug("Search session #" + sessionId + " created with pattern: " + options.getPattern());
    }
    
    /**
     * Add a search result to the session.
     */
    public void addResult(@NotNull SearchResult result) {
        if (results.size() < options.getMaxResults()) {
            results.add(result);
        }
    }
    
    /**
     * Increment files scanned counter and notify progress callback.
     */
    public void incrementFilesScanned() {
        int count = filesScanned.incrementAndGet();
        
        if (progressCallback != null) {
            progressCallback.onProgress(count, results.size(), cancelled.get());
        }
    }
    
    /**
     * Cancel the search session.
     */
    public void cancel() {
        cancelled.set(true);
        completed = true;
        logger.debug("Search session #" + sessionId + " cancelled");
    }
    
    /**
     * Mark the search as completed.
     */
    public void complete() {
        completed = true;
        if (progressCallback != null) {
            progressCallback.onProgress(filesScanned.get(), results.size(), false);
        }
        logger.debug("Search session #" + sessionId + " completed: scanned=" + filesScanned.get() + ", found=" + results.size());
    }
    
    // Getters
    
    public int getSessionId() {
        return sessionId;
    }
    
    public @NotNull SearchOptions getOptions() {
        return options;
    }
    
    public @NotNull List<SearchResult> getResults() {
        return new ArrayList<>(results);
    }
    
    public int getFilesScanned() {
        return filesScanned.get();
    }
    
    public boolean isCancelled() {
        return cancelled.get();
    }
    
    public boolean isCompleted() {
        return completed;
    }
    
    public boolean shouldContinue() {
        return !cancelled.get() && results.size() < options.getMaxResults() && !completed;
    }
    
    /**
     * Set progress callback.
     */
    public void setProgressCallback(@Nullable SearchProgressCallback callback) {
        this.progressCallback = callback;
    }
}
