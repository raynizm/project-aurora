package se.tcmt.aurora.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all Aurora code actions to reduce boilerplate.
 * Handles common logic like getting project, editor, file, and selected text.
 */
public abstract class BaseCodeAction extends AnAction {

    private final String command;
    private final String promptType;

    protected BaseCodeAction(@NotNull String text, @NotNull String command, @NotNull String promptType) {
        super(text);
        this.command = command;
        this.promptType = promptType;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Actions are visible only when there is a selection in the editor.
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(editor != null && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) return;

        var selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        if (selectedText == null || selectedText.isEmpty()) return;

        int startLine = editor.getDocument().getLineNumber(selectionModel.getSelectionStart());
        int endLine = editor.getDocument().getLineNumber(selectionModel.getSelectionEnd());

        // Build the prompt message with context
        String prompt = buildPrompt(promptType, file.getPath(), startLine + 1, endLine + 1, selectedText);

        // Send to Aurora chat panel via JS bridge
        sendToAurora(project, command, prompt);
    }

    /**
     * Builds the prompt text based on action type.
     */
    private String buildPrompt(String promptType, String filePath, int startLine, int endLine, String selectedText) {
        return switch (promptType) {
            case "EXPLAIN" -> """
                Explain the following code from file path %s:%d-%d

                ```
                %s
                ```

                Please provide a clear and concise explanation of what this code does, including:
                1. The purpose and functionality
                2. Key components and their interactions
                3. Important patterns or techniques used""".formatted(filePath, startLine, endLine, selectedText);

            case "FIX" -> """
                Fix any issues in the following code from file path %s:%d-%d

                ```
                %s
                ```

                Please:
                1. Address all detected problems listed above (if any)
                2. Identify any other potential bugs or issues
                3. Provide corrected code
                4. Explain what was fixed and why""".formatted(filePath, startLine, endLine, selectedText);

            case "IMPROVE" -> """
                Improve the following code from file path %s:%d-%d

                ```
                %s
                ```

                Please suggest improvements for:
                1. Code readability and maintainability
                2. Performance optimization
                3. Best practices and patterns
                4. Error handling and edge cases

                Provide the improved code along with explanations for each enhancement.""" .formatted(filePath, startLine, endLine, selectedText);

            default -> selectedText;
        };
    }

    /**
     * Sends a message to the Aurora chat panel via JS bridge.
     */
    private void sendToAurora(com.intellij.openapi.project.Project project, String command, String prompt) {
        // Find the Aurora tool window and send message
        var toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Aurora");
        if (toolWindow == null) return;

        // Get the chat panel from the tool window content manager
        var contentManager = toolWindow.getContentManager();
        if (contentManager.getContents().length == 0) return;

        var content = contentManager.getContents()[0];
        var component = content.getComponent();
        if (component instanceof se.tcmt.aurora.chat.AuroraChatPanel chatPanel) {
            // Send message via JS bridge
            String jsMessage = String.format(
                "{\"type\":\"invoke\",\"invoke\":\"sendMessage\",\"text\":\"%s\"}",
                prompt.replace("\"", "\\\"").replace("\n", "\\n")
            );
            chatPanel.postMessage(jsMessage);
        }
    }
}
