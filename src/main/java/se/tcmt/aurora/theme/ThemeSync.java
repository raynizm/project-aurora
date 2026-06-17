package se.tcmt.aurora.theme;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Syncs IDE theme (dark/light) to the WebView via CSS variables.
 * Follows Roo-Code's ThemeManager pattern: detect brightness via UIManager,
 * register LafManagerListener for live updates, generate --aurora-* CSS vars.
 */
public class ThemeSync implements Disposable {

    private static final Logger LOG = Logger.getInstance(ThemeSync.class);

    private final Project project;
    private volatile boolean isDarkTheme = true;
    @Nullable
    private ThemeChangeListener listener;

    /**
     * Callback interface for theme change notifications.
     */
    public interface ThemeChangeListener {
        void onThemeChanged(boolean isDark, @NotNull Map<String, String> cssVars);
    }

    public ThemeSync(@NotNull Project project) {
        this.project = project;
        detectCurrentTheme();
    }

    /**
     * Register a listener and start monitoring theme changes.
     */
    public void registerListener(@NotNull ThemeChangeListener listener) {
        this.listener = listener;
        sendInitialTheme();

        // Use message bus to subscribe to LafManager events — fires when user switches themes via Settings → Appearance.
        ApplicationManager.getApplication().getMessageBus()
                .connect(this)  // connect to this Disposable — auto-unsubscribes on dispose()
                .subscribe(LafManagerListener.TOPIC, new LafManagerListener() {
                    private long lastFireTime = 0;

                    @Override
                    public void lookAndFeelChanged(@NotNull LafManager source) {
                        // Debounce: only process once per 500ms to avoid spam during theme switch animation
                        long now = System.currentTimeMillis();
                        if (now - lastFireTime < 500) return;
                        lastFireTime = now;

                        LOG.info("LafManager.lookAndFeelChanged fired");
                        detectCurrentTheme();
                        sendCurrentTheme();
                    }
                });
    }

    /**
     * Detect current IDE theme brightness via UIManager.
     */
    private void detectCurrentTheme() {
        try {
            Color background = UIManager.getColor("Panel.background");
            if (background != null) {
                double brightness = (0.299 * background.getRed() +
                        0.587 * background.getGreen() +
                        0.114 * background.getBlue()) / 255.0;
                isDarkTheme = brightness < 0.5;
                LOG.debug("Detected " + (isDarkTheme ? "dark" : "light") + " theme, brightness=" + String.format("%.3f", brightness));
            } else {
                isDarkTheme = true;
                LOG.warn("Cannot detect theme brightness, defaulting to dark");
            }
        } catch (Exception e) {
            LOG.error("Error detecting theme", e);
            isDarkTheme = true;
        }
    }

    /**
     * Generate CSS variables from UIManager colors.
     */
    @NotNull
    private Map<String, String> generateCssVars() {
        Map<String, String> vars = new HashMap<>();

        // Core background/foreground
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Label.foreground");
        if (bg != null) vars.put("--aurora-bg", toHex(bg));
        if (fg != null) vars.put("--aurora-fg", toHex(fg));

        // Input area
        Color inputBg = UIManager.getColor("TextField.background");
        Color inputFg = UIManager.getColor("TextField.foreground");
        if (inputBg != null) vars.put("--aurora-input-bg", toHex(inputBg));
        if (inputFg != null) vars.put("--aurora-input-fg", toHex(inputFg));

        // Borders and separators
        Color border = UIManager.getColor("Separator.foreground");
        if (border != null) vars.put("--aurora-border", toHex(border));

        // Status bar
        Color statusBg = UIManager.getColor("Panel.background");
        Color statusFg = UIManager.getColor("Label.foreground");
        if (statusBg != null) vars.put("--aurora-status-bg", toHex(statusBg));
        if (statusFg != null) vars.put("--aurora-status-fg", toHex(statusFg));

        // Code blocks
        Color codeBg = UIManager.getColor("TextArea.background");
        Color codeFg = UIManager.getColor("TextArea.foreground");
        if (codeBg != null) vars.put("--aurora-code-bg", toHex(codeBg));
        if (codeFg != null) vars.put("--aurora-code-fg", toHex(codeFg));

        // Inline code
        Color preformatBg = UIManager.getColor("TextArea.background");
        Color preformatFg = UIManager.getColor("Label.foreground");
        if (preformatBg != null) vars.put("--aurora-pre-bg", toHex(preformatBg));
        if (preformatFg != null) vars.put("--aurora-pre-fg", toHex(preformatFg));

        // Links
        Color linkFg = UIManager.getColor("Hyperlink.activeForeground");
        if (linkFg == null) linkFg = UIManager.getColor("Label.foreground");
        if (linkFg != null) vars.put("--aurora-link-fg", toHex(linkFg));

        // Scrollbar
        Color scrollThumb = UIManager.getColor("ScrollBar.thumb");
        if (scrollThumb != null) vars.put("--aurora-scrollbar-thumb", toHex(scrollThumb));
        Color scrollTrack = UIManager.getColor("ScrollBar.track");
        if (scrollTrack != null) vars.put("--aurora-scrollbar-track", toHex(scrollTrack));

        // Selection
        Color selectionBg = UIManager.getColor("Selection.background");
        Color selectionFg = UIManager.getColor("Selection.foreground");
        if (selectionBg != null) vars.put("--aurora-selection-bg", toHex(selectionBg));
        if (selectionFg != null) vars.put("--aurora-selection-fg", toHex(selectionFg));

        // Welcome message
        Color welcomeFg = UIManager.getColor("Label.foreground");
        if (welcomeFg != null) {
            int r = Math.min(255, welcomeFg.getRed() + 60);
            int g = Math.min(255, welcomeFg.getGreen() + 60);
            int b = Math.min(255, welcomeFg.getBlue() + 60);
            vars.put("--aurora-welcome-fg", String.format("#%02x%02x%02x", r, g, b));
        }

        // User message accent
        if (isDarkTheme) {
            vars.put("--aurora-user-accent", "#1a7f37");
            vars.put("--aurora-assistant-header", "#c9d1d9");
        } else {
            vars.put("--aurora-user-accent", "#0969da");
            vars.put("--aurora-assistant-header", "#57606a");
        }

        // Send button
        if (isDarkTheme) {
            vars.put("--aurora-send-bg", "#238636");
            vars.put("--aurora-send-hover", "#2ea043");
        } else {
            vars.put("--aurora-send-bg", "#0969da");
            vars.put("--aurora-send-hover", "#0550ae");
        }

        // Streaming cursor
        if (isDarkTheme) {
            vars.put("--aurora-cursor-color", "#58a6ff");
        } else {
            vars.put("--aurora-cursor-color", "#0969da");
        }

        return vars;
    }

    /**
     * Send initial theme to listener.
     */
    private void sendInitialTheme() {
        if (listener != null) {
            Map<String, String> cssVars = generateCssVars();
            LOG.debug("Sending initial theme: " + (isDarkTheme ? "dark" : "light"));
            listener.onThemeChanged(isDarkTheme, cssVars);
        }
    }

    /**
     * Send current theme to listener.
     */
    private void sendCurrentTheme() {
        if (listener != null) {
            Map<String, String> cssVars = generateCssVars();
            LOG.debug("Sending updated theme: " + (isDarkTheme ? "dark" : "light"));
            listener.onThemeChanged(isDarkTheme, cssVars);
        }
    }

    /**
     * Convert Color to hex string.
     */
    @NotNull
    private static String toHex(@NotNull Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Check if current theme is dark.
     */
    public boolean isDarkTheme() {
        return isDarkTheme;
    }

    @Override
    public void dispose() {
        LOG.debug("ThemeSync disposed");
    }
}
