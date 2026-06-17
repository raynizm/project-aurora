// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * IntelliJ service for managing terminal instances.
 * Registered as a project-scoped service via @Service annotation.
 * Mirrors Roo-Code's TerminalService registration pattern.
 */
@Service(Service.Level.PROJECT)
public final class TerminalManager {
    
    private static final Logger logger = Logger.getInstance(TerminalManager.class);
    
    private final Project project;
    private TerminalService terminalService;
    
    public TerminalManager(@NotNull Project project) {
        this.project = project;
        logger.debug("TerminalManager created for project: " + project.getName());
    }
    
    /**
     * Get or create the underlying terminal service instance.
     */
    private @NotNull TerminalService getOrCreateService() {
        if (terminalService == null) {
            terminalService = new TerminalService(project);
            logger.debug("TerminalService initialized");
        }
        return terminalService;
    }
    
    /**
     * Create a new terminal with the given configuration.
     */
    public @NotNull TerminalInstance createTerminal(@NotNull String name) {
        TerminalConfig config = new TerminalConfig(
                name,
                null, // Use default shell
                null,
                project.getBasePath(),
                null,
                null,
                project
        );
        
        return getOrCreateService().createTerminal(config);
    }
    
    /**
     * Create a terminal with custom configuration.
     */
    public @NotNull TerminalInstance createTerminal(@NotNull TerminalConfig config) {
        return getOrCreateService().createTerminal(config);
    }
    
    /**
     * Send text to the first available terminal.
     */
    public void sendTextToFirst(@NotNull String text, boolean shouldExecute) {
        if (terminalService != null) {
            terminalService.sendTextToFirst(text, shouldExecute);
        } else {
            logger.warn("No terminal service initialized");
        }
    }
    
    /**
     * Send text to a specific terminal.
     */
    public void sendText(@NotNull String extHostId, @NotNull String text, boolean shouldExecute) {
        if (terminalService != null) {
            terminalService.sendText(extHostId, text, shouldExecute);
        } else {
            logger.warn("No terminal service initialized");
        }
    }
    
    /**
     * Get the number of active terminals.
     */
    public int getTerminalCount() {
        if (terminalService != null) {
            return terminalService.getTerminalCount();
        }
        return 0;
    }
    
    /**
     * Close all terminals.
     */
    public void closeAllTerminals() {
        if (terminalService != null) {
            terminalService.closeAll();
        }
    }
    
    /**
     * Check if any terminals are active.
     */
    public boolean hasActiveTerminals() {
        return terminalService != null && terminalService.getTerminalCount() > 0;
    }
}
