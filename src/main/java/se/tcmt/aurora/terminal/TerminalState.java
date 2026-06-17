// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Manages terminal lifecycle state to prevent invalid operations.
 * Mirrors Roo-Code's TerminalState for safe terminal management.
 */
public class TerminalState {
    
    private static final Logger logger = Logger.getInstance(TerminalState.class);
    
    // Terminal states
    public enum State {
        CREATED,
        INITIALIZING,
        INITIALIZED,
        DISPOSED
    }
    
    private volatile State currentState = State.CREATED;
    private final String terminalId;
    
    public TerminalState(@NotNull String terminalId) {
        this.terminalId = terminalId;
    }
    
    /**
     * Check if terminal can be initialized (must be in CREATED state)
     */
    public void checkCanInitialize(String terminalId) {
        synchronized (this) {
            if (currentState != State.CREATED) {
                logger.error("Terminal already initialized or disposed, cannot initialize: " + terminalId);
                throw new IllegalStateException("Terminal already initialized or disposed: " + terminalId);
            }
        }
    }
    
    /**
     * Mark terminal as initializing
     */
    public void markInitializing() {
        synchronized (this) {
            currentState = State.INITIALIZING;
        }
    }
    
    /**
     * Mark terminal as initialized
     */
    public void markInitialized() {
        synchronized (this) {
            currentState = State.INITIALIZED;
        }
    }
    
    /**
     * Mark terminal as disposed
     */
    public void markDisposed() {
        synchronized (this) {
            currentState = State.DISPOSED;
        }
    }
    
    /**
     * Check if terminal can perform operations (must be INITIALIZED state)
     */
    public boolean canOperate() {
        return currentState == State.INITIALIZED;
    }
    
    /**
     * Check if terminal is disposed
     */
    public boolean isDisposed() {
        return currentState == State.DISPOSED;
    }
    
    /**
     * Get current state
     */
    public @NotNull State getCurrentState() {
        return currentState;
    }
}
