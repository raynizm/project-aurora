// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.logger;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Output channel service for managing output channels in the IDE.
 * Mirrors the reference plugin's MainThreadOutputServiceShape.
 */
public final class OutputChannelService implements com.intellij.openapi.Disposable {
    private static final Logger LOG = Logger.getInstance(OutputChannelService.class);

    private final Map<String, OutputChannel> channels;
    private volatile boolean disposed = false;

    public OutputChannelService() {
        this.channels = new HashMap<>();
        LOG.debug("Initialized OutputChannelService");
    }

    /**
     * Register a new output channel.
     */
    @NotNull
    public String register(@NotNull String label, 
                           @Nullable Map<String, Object> fileComponents,
                           @Nullable String languageId,
                           @NotNull String extensionId) {
        if (disposed) {
            LOG.warn("Cannot register channel on disposed service");
            return "";
        }

        String channelId = UUID.randomUUID().toString();
        
        OutputChannel channel = new OutputChannel(channelId, label, fileComponents, languageId, extensionId);
        channels.put(channelId, channel);
        
        LOG.debug("Registered output channel: id=" + channelId + ", label=" + label + 
                  ", extensionId=" + extensionId);
        
        return channelId;
    }

    /**
     * Update an output channel.
     */
    public void update(@NotNull String channelId, int mode, @Nullable Integer till) {
        if (disposed) {
            return;
        }

        OutputChannel channel = channels.get(channelId);
        if (channel != null) {
            LOG.debug("Updating output channel: id=" + channelId + ", mode=" + mode + 
                      (till != null ? ", till=" + till : ""));
            
            // Update channel state based on mode
            switch (mode) {
                case 0: // Append mode
                    break;
                case 1: // Replace mode
                    channel.clear();
                    break;
                default:
                    LOG.warn("Unknown update mode: " + mode);
            }
        } else {
            LOG.warn("Output channel not found for update: " + channelId);
        }
    }

    /**
     * Reveal an output channel.
     */
    public void reveal(@NotNull String channelId, boolean preserveFocus) {
        if (disposed) {
            return;
        }

        OutputChannel channel = channels.get(channelId);
        if (channel != null) {
            LOG.debug("Revealing output channel: id=" + channelId + ", preserveFocus=" + preserveFocus);
            
            // Mark as visible/active
            channel.setVisible(true);
            channel.setActive(true);
        } else {
            LOG.warn("Output channel not found for reveal: " + channelId);
        }
    }

    /**
     * Close an output channel.
     */
    public void close(@NotNull String channelId) {
        if (disposed) {
            return;
        }

        OutputChannel channel = channels.get(channelId);
        if (channel != null) {
            LOG.debug("Closing output channel: id=" + channelId);
            
            // Close the underlying stream/file
            channel.close();
        } else {
            LOG.warn("Output channel not found for close: " + channelId);
        }
    }

    /**
     * Dispose an output channel.
     */
    public void dispose(@NotNull String channelId) {
        if (disposed) {
            return;
        }

        OutputChannel removed = channels.remove(channelId);
        if (removed != null) {
            LOG.debug("Disposing output channel: id=" + channelId);
            removed.dispose();
        } else {
            LOG.warn("Output channel not found for dispose: " + channelId);
        }
    }

    /**
     * Get all registered channels.
     */
    public @NotNull Map<String, OutputChannel> getChannels() {
        return new HashMap<>(channels);
    }

    /**
     * Get a specific channel by ID.
     */
    public @Nullable OutputChannel getChannel(@NotNull String channelId) {
        return channels.get(channelId);
    }

    /**
     * Get all channel IDs.
     */
    public @NotNull List<String> getAllChannelIds() {
        return new ArrayList<>(channels.keySet());
    }

    @Override
    public void dispose() {
        disposed = true;
        LOG.debug("Disposing OutputChannelService");
        
        // Dispose all channels
        for (OutputChannel channel : channels.values()) {
            channel.dispose();
        }
        channels.clear();
    }

    /**
     * Represents a single output channel.
     */
    public static class OutputChannel implements com.intellij.openapi.Disposable {
        private final String id;
        private final String label;
        private final Map<String, Object> fileComponents;
        private final String languageId;
        private final String extensionId;
        private volatile boolean visible = false;
        private volatile boolean active = false;
        private volatile boolean closed = false;

        public OutputChannel(@NotNull String id, @NotNull String label,
                             @Nullable Map<String, Object> fileComponents,
                             @Nullable String languageId,
                             @NotNull String extensionId) {
            this.id = id;
            this.label = label;
            this.fileComponents = fileComponents != null ? new HashMap<>(fileComponents) : new HashMap<>();
            this.languageId = languageId;
            this.extensionId = extensionId;
        }

        public @NotNull String getId() { return id; }
        public @NotNull String getLabel() { return label; }
        
        public boolean isVisible() { return visible; }
        public void setVisible(boolean visible) { this.visible = visible; }
        
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        
        public boolean isClosed() { return closed; }
        
        public @Nullable String getLanguageId() { return languageId; }
        public @NotNull String getExtensionId() { return extensionId; }

        public void clear() {
            // Clear channel content (in a full implementation, this would clear the underlying buffer)
        }

        public void close() {
            closed = true;
        }

        @Override
        public void dispose() {
            closed = true;
            visible = false;
            active = false;
        }
    }
}
