// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * Handles VS Code shell integration protocol for terminal instances.
 * Mirrors Roo-Code's TerminalShellIntegration for command tracking and environment variable reporting.
 */
public class TerminalShellIntegration {
    
    private static final Logger logger = Logger.getInstance(TerminalShellIntegration.class);
    
    private final String extHostTerminalId;
    private final int numericId;
    
    // Shell integration state
    private volatile boolean shellIntegrationEnabled = false;
    private volatile long lastPromptEndLine = 0;
    private volatile String cwd = null;
    
    public TerminalShellIntegration(
            @NotNull String extHostTerminalId,
            int numericId) {
        this.extHostTerminalId = extHostTerminalId;
        this.numericId = numericId;
    }
    
    /**
     * Setup shell integration by injecting VS Code shell integration markers
     */
    public void setupShellIntegration() {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                logger.debug("Setting up shell integration for terminal: " + extHostTerminalId);
                // Shell integration is handled via TerminalCustomizer injection
                // This class manages the state and notifications
                shellIntegrationEnabled = true;
                notifyShellIntegrationChange();
            } catch (Exception e) {
                logger.error("Failed to setup shell integration", e);
            }
        });
    }
    
    /**
     * Append raw terminal output for shell integration parsing
     */
    public void appendRawOutput(@NotNull String data) {
        if (!shellIntegrationEnabled) {
            return;
        }
        
        // Parse VS Code shell integration markers from terminal output
        // These markers are injected by the shell integration scripts
        parseShellIntegrationMarkers(data);
    }
    
    /**
     * Parse VS Code shell integration markers from terminal output
     */
    private void parseShellIntegrationMarkers(@NotNull String data) {
        // Look for prompt start marker: \x1b]633;A\x07
        if (data.contains("\u001b]633;A\u0007")) {
            lastPromptEndLine++;
            logger.debug("Shell integration: prompt end detected, line=" + lastPromptEndLine);
        }
        
        // Look for cwd marker: \x1b]633;C;<path>\x07
        if (data.contains("\u001b]633;C;")) {
            int startIndex = data.indexOf("\u001b]633;C;");
            if (startIndex >= 0) {
                int endIndex = data.indexOf("\u0007", startIndex);
                if (endIndex > startIndex) {
                    String cwdData = data.substring(startIndex + 9, endIndex);
                    this.cwd = cwdData;
                    logger.debug("Shell integration: cwd updated to " + cwd);
                }
            }
        }
        
        // Look for env var markers: \x1b]633;E;<name>=<value>\x07
        if (data.contains("\u001b]633;E;")) {
            int startIndex = data.indexOf("\u001b]633;E;");
            if (startIndex >= 0) {
                int endIndex = data.indexOf("\u0007", startIndex);
                if (endIndex > startIndex) {
                    String envData = data.substring(startIndex + 9, endIndex);
                    logger.debug("Shell integration: env var detected: " + envData);
                }
            }
        }
    }
    
    /**
     * Notify shell integration change event
     */
    private void notifyShellIntegrationChange() {
        // In full implementation, this would notify the ExtHost process via RPC protocol
        logger.debug("Shell integration initialized for terminal: " + extHostTerminalId);
    }
    
    /**
     * Get current working directory from shell integration
     */
    public @NotNull String getCwd() {
        return cwd != null ? cwd : "";
    }
    
    /**
     * Check if shell integration is enabled
     */
    public boolean isShellIntegrationEnabled() {
        return shellIntegrationEnabled;
    }
    
    /**
     * Get last prompt end line number
     */
    public long getLastPromptEndLine() {
        return lastPromptEndLine;
    }
}
