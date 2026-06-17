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

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Terminal service that manages terminal instances for the Aurora plugin.
 * Mirrors Roo-Code's TerminalService for VS Code extension host compatibility.
 * 
 * Features:
 * - Create/manage multiple terminal instances
 * - Send text/commands to terminals
 * - Capture terminal output via callbacks
 * - Shell integration (VS Code protocol)
 * - Lifecycle management with project disposal
 */
public class TerminalService implements Disposable {
    
    private static final Logger logger = Logger.getInstance(TerminalService.class);
    private static final String TERMINAL_TOOL_WINDOW_ID = "Terminal";
    
    private final Project project;
    private final Map<String, TerminalInstance> terminals = new ConcurrentHashMap<>();
    private int nextNumericId = 1;
    
    public TerminalService(@NotNull Project project) {
        this.project = project;
        // Register for disposal when project closes
        Disposer.register(project, this);
        
        // Force UTF-8 on Windows
        if (com.intellij.openapi.util.SystemInfo.isWindows) {
            logger.debug("Windows detected - will inject chcp 65001 for UTF-8 support");
        }
    }
    
    /**
     * Create a new terminal instance with the given configuration.
     */
    public @NotNull TerminalInstance createTerminal(@NotNull TerminalConfig config) {
        String extHostId = UUID.randomUUID().toString();
        int numericId = nextNumericId++;
        
        logger.info("Creating terminal: " + config.getName() + " (id=" + numericId + ")");
        
        // Create and initialize the terminal instance
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                TerminalInstance instance = new TerminalInstance(
                        extHostId,
                        numericId,
                        project,
                        config
                );
                instance.initialize();
                terminals.put(extHostId, instance);
                
                // Register close callback to clean up
                instance.addTerminalCloseCallback(() -> {
                    logger.info("Terminal closed: " + extHostId);
                    terminals.remove(extHostId);
                });
            } catch (Exception e) {
                logger.error("Failed to create terminal", e);
                throw new RuntimeException("Failed to create terminal", e);
            }
        });
        
        return terminals.get(extHostId);
    }
    
    /**
     * Get a terminal instance by its external ID.
     */
    public @Nullable TerminalInstance getTerminal(@NotNull String extHostId) {
        return terminals.get(extHostId);
    }
    
    /**
     * Send text to a specific terminal.
     */
    public void sendText(@NotNull String extHostId, @NotNull String text, boolean shouldExecute) {
        TerminalInstance instance = terminals.get(extHostId);
        if (instance != null) {
            instance.sendText(text, shouldExecute);
        } else {
            logger.warn("Terminal not found: " + extHostId);
        }
    }
    
    /**
     * Send text to the first available terminal.
     */
    public void sendTextToFirst(@NotNull String text, boolean shouldExecute) {
        if (!terminals.isEmpty()) {
            String firstId = terminals.keySet().iterator().next();
            sendText(firstId, text, shouldExecute);
        } else {
            logger.warn("No terminals available");
        }
    }
    
    /**
     * Get all terminal IDs.
     */
    public @NotNull java.util.Set<String> getTerminalIds() {
        return terminals.keySet();
    }
    
    /**
     * Get the number of active terminals.
     */
    public int getTerminalCount() {
        return terminals.size();
    }
    
    /**
     * Close a specific terminal.
     */
    public void closeTerminal(@NotNull String extHostId) {
        TerminalInstance instance = terminals.get(extHostId);
        if (instance != null) {
            Disposer.dispose(instance);
            terminals.remove(extHostId);
        }
    }
    
    /**
     * Close all terminals.
     */
    public void closeAll() {
        for (String id : terminals.keySet()) {
            closeTerminal(id);
        }
    }
    
    @Override
    public void dispose() {
        logger.debug("Disposing TerminalService");
        closeAll();
    }
}
