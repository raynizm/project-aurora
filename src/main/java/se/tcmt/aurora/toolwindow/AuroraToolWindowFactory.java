package se.tcmt.aurora.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import se.tcmt.aurora.chat.AuroraChatPanel;

/**
 * Factory for creating the Aurora tool window.
 */
public class AuroraToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AuroraChatPanel chatPanel = new AuroraChatPanel(project);

        // Inject provider and config into the panel
        se.tcmt.aurora.provider.OpenAiProvider openAiProvider = new se.tcmt.aurora.provider.OpenAiProvider();
        se.tcmt.aurora.settings.AuroraSettingsState settings = se.tcmt.aurora.settings.AuroraSettingsState.getInstance();
        chatPanel.setActiveProvider(openAiProvider);
        chatPanel.setProviderConfig(settings.toProviderConfig());

        // Add editor listener to update context when editor changes
        com.intellij.openapi.editor.ex.EditorEx editor = getActiveEditor(project);
        if (editor != null) {
            chatPanel.setCurrentEditor(editor);
        }

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(chatPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Nullable
    private com.intellij.openapi.editor.ex.EditorEx getActiveEditor(@NotNull Project project) {
        com.intellij.openapi.wm.FocusManager focusManager = com.intellij.openapi.wm.FocusManager.getInstance(project);
        com.intellij.openapi.actionSystem.DataContext context = focusManager.getContext();
        
        com.intellij.openapi.editor.Editor editor = com.intellij.openapi.editor.EditorFactory.getInstance()
            .getAllEditors()
            .stream()
            .filter(e -> e.getProject() == project)
            .findFirst()
            .orElse(null);
            
        return (editor instanceof com.intellij.openapi.editor.ex.EditorEx) 
            ? (com.intellij.openapi.editor.ex.EditorEx) editor 
            : null;
    }
}
