// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Proxy PtyProcess that intercepts terminal input/output streams for shell integration.
 * Mirrors Roo-Code's ProxyPtyProcess for VS Code extension host compatibility.
 */
public class ProxyPtyProcess implements AutoCloseable {
    
    private static final Logger logger = Logger.getInstance(ProxyPtyProcess.class);
    
    private final Process underlyingProcess;
    private final Charset charset;
    @Nullable
    private final ProxyPtyProcessCallback callback;
    
    /**
     * Callback interface for raw terminal data events
     */
    public interface ProxyPtyProcessCallback {
        void onRawData(@NotNull String data, @NotNull String streamType);
    }
    
    public ProxyPtyProcess(
            @NotNull Process underlyingProcess,
            @NotNull Charset charset,
            @Nullable ProxyPtyProcessCallback callback) {
        this.underlyingProcess = underlyingProcess;
        this.charset = charset;
        this.callback = callback;
        
        // Start background threads to intercept output streams
        startOutputInterceptors();
    }
    
    /**
     * Start background threads to intercept stdout and stderr
     */
    private void startOutputInterceptors() {
        // Intercept stdout
        Thread stdoutThread = new Thread(() -> {
            try (InputStream inputStream = underlyingProcess.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    String data = new String(buffer, 0, bytesRead, charset);
                    if (callback != null) {
                        callback.onRawData(data, "stdout");
                    }
                }
            } catch (Exception e) {
                logger.debug("Stdout stream closed: " + e.getMessage());
            }
        }, "aurora-terminal-stdout-interceptor");
        stdoutThread.setDaemon(true);
        stdoutThread.start();
        
        // Intercept stderr
        Thread stderrThread = new Thread(() -> {
            try (InputStream inputStream = underlyingProcess.getErrorStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    String data = new String(buffer, 0, bytesRead, charset);
                    if (callback != null) {
                        callback.onRawData(data, "stderr");
                    }
                }
            } catch (Exception e) {
                logger.debug("Stderr stream closed: " + e.getMessage());
            }
        }, "aurora-terminal-stderr-interceptor");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }
    
    /**
     * Get the underlying process output stream for writing to terminal
     */
    public @NotNull OutputStream getOutputStream() {
        return underlyingProcess.getOutputStream();
    }
    
    /**
     * Get the exit code of the process
     */
    public int waitFor() throws InterruptedException {
        return underlyingProcess.waitFor();
    }
    
    /**
     * Check if process is alive
     */
    public boolean isAlive() {
        return underlyingProcess.isAlive();
    }
    
    /**
     * Destroy the process
     */
    public void destroy() {
        underlyingProcess.destroy();
    }
    
    /**
     * Destroy the process forcefully
     */
    public void destroyForcibly() {
        underlyingProcess.destroyForcibly();
    }
    
    @Override
    public void close() {
        destroy();
    }
}
