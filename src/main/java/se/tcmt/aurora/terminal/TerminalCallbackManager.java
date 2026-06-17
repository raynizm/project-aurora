// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages terminal event callbacks (close, data, etc.).
 * Mirrors Roo-Code's TerminalCallbackManager for event-driven terminal management.
 */
public class TerminalCallbackManager {
    
    private final List<Runnable> closeCallbacks = new CopyOnWriteArrayList<>();
    private final List<TerminalDataCallback> dataCallbacks = new CopyOnWriteArrayList<>();
    
    /**
     * Add terminal close listener
     */
    public void addCloseCallback(@NotNull Runnable callback) {
        closeCallbacks.add(callback);
    }
    
    /**
     * Remove terminal close listener
     */
    public void removeCloseCallback(@NotNull Runnable callback) {
        closeCallbacks.remove(callback);
    }
    
    /**
     * Notify all close callbacks
     */
    public void notifyClose() {
        for (Runnable callback : closeCallbacks) {
            try {
                callback.run();
            } catch (Exception e) {
                // Ignore exceptions in callbacks
            }
        }
    }
    
    /**
     * Add terminal data listener
     */
    public void addDataCallback(@NotNull TerminalDataCallback callback) {
        dataCallbacks.add(callback);
    }
    
    /**
     * Remove terminal data listener
     */
    public void removeDataCallback(@NotNull TerminalDataCallback callback) {
        dataCallbacks.remove(callback);
    }
    
    /**
     * Notify all data callbacks with raw output
     */
    public void notifyRawOutput(@NotNull String data, @NotNull String streamType) {
        for (TerminalDataCallback callback : dataCallbacks) {
            try {
                callback.onRawData(data, streamType);
            } catch (Exception e) {
                // Ignore exceptions in callbacks
            }
        }
    }
    
    /**
     * Callback interface for terminal raw data events
     */
    public interface TerminalDataCallback {
        void onRawData(@NotNull String data, @NotNull String streamType);
    }
}
