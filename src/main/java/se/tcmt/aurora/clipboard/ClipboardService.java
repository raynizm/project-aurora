// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.clipboard;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

/**
 * IntelliJ service for clipboard operations.
 * Mirrors Roo-Code's MainThreadClipboardShape interface and implementation.
 * 
 * Provides:
 * - Reading text from system clipboard
 * - Writing text to system clipboard
 */
public final class ClipboardService {

    private static final Logger logger = Logger.getInstance(ClipboardService.class);

    @Nullable private Clipboard clipboard;

    public ClipboardService() {
        try {
            this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            logger.debug("ClipboardService initialized");
        } catch (Exception e) {
            logger.warn("[Clipboard] Failed to initialize system clipboard", e);
            this.clipboard = null;
        }
    }

    /**
     * Read text from the system clipboard.
     * 
     * @return The string from the clipboard, or null if no text is available
     */
    @Nullable public String readText() {
        logger.debug("[Clipboard] Reading clipboard text");
        
        try {
            Clipboard sysClipboard = getClipboard();
            if (sysClipboard == null) {
                logger.warn("[Clipboard] System clipboard not available");
                return null;
            }

            Transferable data = sysClipboard.getContents(null);
            if (data != null && data.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String text = (String) data.getTransferData(DataFlavor.stringFlavor);
                logger.debug("[Clipboard] Read " + text.length() + " characters from clipboard");
                return text;
            } else {
                logger.debug("[Clipboard] No string data available in clipboard");
                return null;
            }
        } catch (Exception e) {
            logger.error("[Clipboard] Failed to read clipboard", e);
            return null;
        }
    }

    /**
     * Write text to the system clipboard.
     * 
     * @param value The string to write to the clipboard, or null to clear
     */
    public void writeText(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            logger.debug("[Clipboard] Clearing clipboard");
            try {
                Clipboard sysClipboard = getClipboard();
                if (sysClipboard != null) {
                    sysClipboard.setContents(null, null);
                }
            } catch (Exception e) {
                logger.error("[Clipboard] Failed to clear clipboard", e);
            }
            return;
        }

        logger.debug("[Clipboard] Writing " + value.length() + " characters to clipboard");
        
        try {
            Clipboard sysClipboard = getClipboard();
            if (sysClipboard == null) {
                logger.warn("[Clipboard] System clipboard not available");
                return;
            }

            StringSelection selection = new StringSelection(value);
            sysClipboard.setContents(selection, selection);
            logger.debug("[Clipboard] Successfully wrote to clipboard");
        } catch (Exception e) {
            logger.error("[Clipboard] Failed to write to clipboard", e);
        }
    }

    /**
     * Get the system clipboard instance.
     */
    @Nullable private Clipboard getClipboard() {
        if (clipboard == null) {
            try {
                clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            } catch (Exception e) {
                logger.warn("[Clipboard] Failed to reinitialize system clipboard", e);
            }
        }
        return clipboard;
    }

    /**
     * Check if the clipboard is available.
     */
    public boolean isAvailable() {
        return getClipboard() != null;
    }

    /**
     * Dispose of resources.
     */
    public void dispose() {
        logger.debug("[Clipboard] Disposing ClipboardService");
        clipboard = null;
    }
}
