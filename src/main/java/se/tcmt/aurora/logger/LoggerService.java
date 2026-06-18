// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.logger;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Logger service for managing log files and output channels.
 * Mirrors the reference plugin's MainThreadLoggerShape and MainThreadOutputServiceShape.
 */
public final class LoggerService implements com.intellij.openapi.Disposable {
    private static final Logger LOG = Logger.getInstance(LoggerService.class);

    private final Map<String, LogChannel> channels;
    private final List<LogEntry> recentEntries;
    private Path logDirectory;

    public LoggerService() {
        this.channels = new java.util.HashMap<>();
        this.recentEntries = new CopyOnWriteArrayList<>();
        
        // Set up log directory in user home
        String userHome = System.getProperty("user.home");
        this.logDirectory = Paths.get(userHome, ".aurora", "logs");
        
        try {
            Files.createDirectories(logDirectory);
            LOG.debug("Initialized LoggerService with log directory: " + logDirectory);
        } catch (IOException e) {
            LOG.warn("Failed to create log directory: " + logDirectory, e);
            this.logDirectory = null;
        }
    }

    /**
     * Log messages to a file.
     */
    public void logToFile(@NotNull String filePath, @NotNull List<String> messages) {
        if (logDirectory == null || filePath.isEmpty()) {
            return;
        }

        try {
            Path path = Paths.get(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            boolean append = Files.exists(path);
            try (BufferedWriter writer = Files.newBufferedWriter(path, 
                    java.nio.file.StandardOpenOption.CREATE, 
                    java.nio.file.StandardOpenOption.WRITE,
                    append ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                
                for (String message : messages) {
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    writer.write("[" + timestamp + "] " + message + "\n");
                    
                    // Add to recent entries
                    recentEntries.add(new LogEntry(timestamp, filePath, message));
                }
            }

            LOG.debug("Logged " + messages.size() + " messages to: " + filePath);
        } catch (IOException e) {
            LOG.error("Failed to log to file: " + filePath, e);
        }
    }

    /**
     * Flush a log file.
     */
    public void flush(@NotNull String filePath) {
        // In Java, buffered writers auto-flush on close
        // This method is kept for API compatibility
        LOG.debug("Flush requested for: " + filePath);
    }

    /**
     * Create a new logger instance.
     */
    public @Nullable LogChannel createLogger(@NotNull String name, 
                                              @Nullable Map<String, Object> options) {
        try {
            LogChannel channel = new LogChannel(name, options);
            channels.put(name, channel);
            LOG.debug("Created logger: " + name);
            return channel;
        } catch (Exception e) {
            LOG.error("Failed to create logger: " + name, e);
            return null;
        }
    }

    /**
     * Register a logger.
     */
    public boolean registerLogger(@NotNull String name, @Nullable Map<String, Object> config) {
        if (channels.containsKey(name)) {
            LOG.warn("Logger already registered: " + name);
            return false;
        }
        
        LogChannel channel = new LogChannel(name, config);
        channels.put(name, channel);
        LOG.debug("Registered logger: " + name);
        return true;
    }

    /**
     * Deregister a logger.
     */
    public boolean deregisterLogger(@NotNull String name) {
        LogChannel removed = channels.remove(name);
        if (removed != null) {
            removed.dispose();
            LOG.debug("Deregistered logger: " + name);
            return true;
        }
        LOG.warn("Logger not found for deregistration: " + name);
        return false;
    }

    /**
     * Set logger visibility.
     */
    public void setVisibility(@NotNull String name, boolean visible) {
        LogChannel channel = channels.get(name);
        if (channel != null) {
            channel.setVisible(visible);
            LOG.debug("Set visibility for " + name + ": " + visible);
        } else {
            LOG.warn("Logger not found for visibility change: " + name);
        }
    }

    /**
     * Get recent log entries.
     */
    public @NotNull List<LogEntry> getRecentEntries(int limit) {
        int start = Math.max(0, recentEntries.size() - limit);
        return new ArrayList<>(recentEntries.subList(start, recentEntries.size()));
    }

    /**
     * Get all registered channels.
     */
    public @NotNull Map<String, LogChannel> getChannels() {
        return new java.util.HashMap<>(channels);
    }

    @Override
    public void dispose() {
        LOG.debug("Disposing LoggerService");
        
        // Dispose all channels
        for (LogChannel channel : channels.values()) {
            channel.dispose();
        }
        channels.clear();
        recentEntries.clear();
    }

    /**
     * Represents a log channel with visibility and configuration.
     */
    public static class LogChannel implements com.intellij.openapi.Disposable {
        private final String name;
        private final Map<String, Object> config;
        private volatile boolean visible = true;
        private volatile boolean disposed = false;

        public LogChannel(@NotNull String name, @Nullable Map<String, Object> config) {
            this.name = name;
            this.config = config != null ? new java.util.HashMap<>(config) : new java.util.HashMap<>();
        }

        public @NotNull String getName() {
            return name;
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
        }

        public @Nullable Object getConfig(@NotNull String key) {
            return config.get(key);
        }

        @Override
        public void dispose() {
            disposed = true;
            LOG.debug("Disposed LogChannel: " + name);
        }
    }

    /**
     * Represents a single log entry.
     */
    public static class LogEntry {
        private final String timestamp;
        private final String filePath;
        private final String message;

        public LogEntry(@NotNull String timestamp, @NotNull String filePath, 
                        @NotNull String message) {
            this.timestamp = timestamp;
            this.filePath = filePath;
            this.message = message;
        }

        public @NotNull String getTimestamp() {
            return timestamp;
        }

        public @NotNull String getFilePath() {
            return filePath;
        }

        public @NotNull String getMessage() {
            return message;
        }
    }
}
