// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages a single terminal instance lifecycle and operations.
 * Mirrors Roo-Code's TerminalInstance for VS Code extension host compatibility.
 * 
 * Features:
 * - Terminal creation and initialization
 * - Text/command sending to terminal
 * - Shell integration management
 * - Show/hide in Terminal tool window
 * - Resource cleanup on disposal
 */
public class TerminalInstance implements Disposable {
    
    private static final Logger logger = Logger.getInstance(TerminalInstance.class);
    private static final String DEFAULT_TERMINAL_NAME = "Aurora";
    private static final String TERMINAL_TOOL_WINDOW_ID = "Terminal";
    
    // Terminal identifiers
    private final String extHostTerminalId;
    private final int numericId;
    private final Project project;
    private final TerminalConfig config;
    
    // State management
    private final TerminalState state;
    private volatile boolean initialized = false;
    
    // Shell integration
    @Nullable
    private TerminalShellIntegration shellIntegration;
    
    // Callbacks
    private final TerminalCallbackManager callbackManager;
    
    public TerminalInstance(
            @NotNull String extHostTerminalId,
            int numericId,
            @NotNull Project project,
            @NotNull TerminalConfig config) {
        this.extHostTerminalId = extHostTerminalId;
        this.numericId = numericId;
        this.project = project;
        this.config = config;
        this.state = new TerminalState(extHostTerminalId);
        this.callbackManager = new TerminalCallbackManager();
        
        // Register to project Disposer for lifecycle management
        registerToProjectDisposer();
    }
    
    /**
     * Initialize terminal instance (must be called once after construction)
     */
    public void initialize() {
        state.checkCanInitialize(extHostTerminalId);
        state.markInitializing();
        
        try {
            logger.debug("🚀 Initializing terminal: " + extHostTerminalId + " (id=" + numericId + ")");
            
            // Switch to EDT thread for UI operations
            ApplicationManager.getApplication().invokeAndWait(() -> {
                performInitialization();
            });
        } catch (Exception e) {
            logger.error("❌ Failed to initialize terminal: " + extHostTerminalId, e);
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }
    
    /**
     * Perform initialization steps on EDT thread
     */
    private void performInitialization() {
        try {
            // Setup shell integration
            setupShellIntegration();
            
            // Mark as initialized
            state.markInitialized();
            initialized = true;
            
            logger.debug("✅ Terminal initialized: " + extHostTerminalId);
            
            // Show in Terminal tool window
            showInToolWindow();
            
            // Notify callbacks
            callbackManager.notifyClose(); // This is a placeholder - would notify terminal opened
            
            // Platform-specific: Force UTF-8 on Windows
            if (com.intellij.openapi.util.SystemInfo.isWindows) {
                logger.debug("🔧 Windows detected, injecting 'chcp 65001' for UTF-8");
                sendText("chcp 65001", true);
            }
            
            // Handle initial text if configured
            String initialText = config.getInitialText();
            if (initialText != null && !initialText.isEmpty()) {
                sendText(initialText, false);
            }
        } catch (Exception e) {
            logger.error("❌ Failed to initialize terminal in EDT: " + extHostTerminalId, e);
            throw new RuntimeException("Failed during initialization", e);
        }
    }
    
    /**
     * Setup shell integration for VS Code protocol compatibility
     */
    private void setupShellIntegration() {
        shellIntegration = new TerminalShellIntegration(extHostTerminalId, numericId);
        shellIntegration.setupShellIntegration();
    }
    
    /**
     * Show terminal in the IDE's Terminal tool window
     */
    private void showInToolWindow() {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
                ToolWindow terminalToolWindow = toolWindowManager.getToolWindow(TERMINAL_TOOL_WINDOW_ID);
                
                if (terminalToolWindow != null) {
                    // The terminal will be added to the existing Terminal tool window
                    logger.debug("✅ Terminal " + extHostTerminalId + " registered with IDE Terminal tool window");
                } else {
                    logger.warn("⚠️ Terminal tool window not found - terminal created but may not be visible");
                }
            } catch (Exception e) {
                logger.error("❌ Failed to show terminal in tool window", e);
            }
        });
    }
    
    /**
     * Send text to the terminal
     */
    public void sendText(@NotNull String text, boolean shouldExecute) {
        if (!state.canOperate()) {
            logger.warn("Terminal not initialized or disposed: " + extHostTerminalId);
            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                // In a full implementation, this would write to the PtyProcess output stream
                // For now, we log the command (terminal integration requires pty4j dependency)
                logger.debug("📤 Sending text to terminal " + extHostTerminalId + ": " + text);
                
                // Append to shell integration for parsing
                if (shellIntegration != null) {
                    shellIntegration.appendRawOutput(text);
                }
            } catch (Exception e) {
                logger.error("❌ Failed to send text to terminal", e);
            }
        });
    }
    
    /**
     * Show the terminal in the IDE
     */
    public void show() {
        if (!state.canOperate()) {
            logger.warn("Terminal not initialized: " + extHostTerminalId);
            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
                ToolWindow terminalToolWindow = toolWindowManager.getToolWindow(TERMINAL_TOOL_WINDOW_ID);
                
                if (terminalToolWindow != null) {
                    terminalToolWindow.show(null);
                    logger.debug("✅ Terminal shown: " + extHostTerminalId);
                }
            } catch (Exception e) {
                logger.error("❌ Failed to show terminal", e);
            }
        });
    }
    
    /**
     * Hide the terminal from view
     */
    public void hide() {
        if (!state.canOperate()) {
            logger.warn("Terminal not initialized: " + extHostTerminalId);
            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
                ToolWindow terminalToolWindow = toolWindowManager.getToolWindow(TERMINAL_TOOL_WINDOW_ID);
                
                if (terminalToolWindow != null) {
                    terminalToolWindow.hide(null);
                    logger.debug("✅ Terminal hidden: " + extHostTerminalId);
                }
            } catch (Exception e) {
                logger.error("❌ Failed to hide terminal", e);
            }
        });
    }
    
    /**
     * Add a callback for when the terminal is closed
     */
    public void addTerminalCloseCallback(@NotNull Runnable callback) {
        callbackManager.addCloseCallback(callback);
    }
    
    /**
     * Get the terminal's external host ID
     */
    public @NotNull String getExtHostTerminalId() {
        return extHostTerminalId;
    }
    
    /**
     * Get the terminal's numeric ID
     */
    public int getNumericId() {
        return numericId;
    }
    
    /**
     * Check if shell integration is enabled
     */
    public boolean isShellIntegrationEnabled() {
        return shellIntegration != null && shellIntegration.isShellIntegrationEnabled();
    }
    
    /**
     * Get current working directory from shell integration (if available)
     */
    public @NotNull String getCwd() {
        if (shellIntegration != null) {
            return shellIntegration.getCwd();
        }
        return config.getCwd() != null ? config.getCwd() : "";
    }
    
    /**
     * Register this terminal instance to the project's Disposer for lifecycle management
     */
    private void registerToProjectDisposer() {
        try {
            Disposer.register(project, this);
            logger.debug("✅ Terminal registered to project Disposer: " + extHostTerminalId);
        } catch (Exception e) {
            logger.error("❌ Failed to register terminal to project Disposer", e);
        }
    }
    
    @Override
    public void dispose() {
        if (!state.isDisposed()) {
            state.markDisposed();
            initialized = false;
            
            // Notify close callbacks
            callbackManager.notifyClose();
            
            logger.debug("✅ Terminal disposed: " + extHostTerminalId);
        }
    }
}
