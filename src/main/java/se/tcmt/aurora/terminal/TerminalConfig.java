// SPDX-FileCopyrightText: 2025 Tyson La (raynizm)
//
// SPDX-License-Identifier: Apache-2.0

package se.tcmt.aurora.terminal;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Terminal configuration parameters for creating and managing terminal instances.
 * Mirrors Roo-Code's TerminalConfig structure for VS Code extension host compatibility.
 */
public class TerminalConfig {
    
    private final String name;
    private final String shellPath;
    private final String[] shellArgs;
    @Nullable
    private final String cwd;
    @Nullable
    private final Map<String, String> env;
    @Nullable
    private final String initialText;
    private final Project project;
    
    public TerminalConfig(
            @NotNull String name,
            @NotNull String shellPath,
            @Nullable String[] shellArgs,
            @Nullable String cwd,
            @Nullable Map<String, String> env,
            @Nullable String initialText,
            @NotNull Project project) {
        this.name = name;
        this.shellPath = shellPath;
        this.shellArgs = shellArgs != null ? shellArgs : new String[0];
        this.cwd = cwd;
        this.env = env != null ? new HashMap<>(env) : null;
        this.initialText = initialText;
        this.project = project;
    }
    
    public @NotNull String getName() {
        return name;
    }
    
    public @NotNull String getShellPath() {
        return shellPath;
    }
    
    public @NotNull String[] getShellArgs() {
        return shellArgs;
    }
    
    public @Nullable String getCwd() {
        return cwd;
    }
    
    public @Nullable Map<String, String> getEnv() {
        return env;
    }
    
    public @Nullable String getInitialText() {
        return initialText;
    }
    
    public @NotNull Project getProject() {
        return project;
    }
}
