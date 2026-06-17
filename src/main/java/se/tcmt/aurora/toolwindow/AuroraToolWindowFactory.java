package se.tcmt.aurora.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import se.tcmt.aurora.chat.AuroraChatPanel;
import se.tcmt.aurora.provider.OpenAiProvider;
import se.tcmt.aurora.settings.AuroraSettingsState;

/**
 * Factory for creating the Aurora tool window.
 */
public class AuroraToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AuroraChatPanel chatPanel = new AuroraChatPanel(project);

        // Get settings state (Roo-Code pattern: use ServiceManager to get persistent settings)
        AuroraSettingsState settings = AuroraSettingsState.getInstance();

        // Inject provider and config into the panel
        OpenAiProvider openAiProvider = new OpenAiProvider();
        chatPanel.setActiveProvider(openAiProvider);
        chatPanel.setSettingsState(settings);

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(chatPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
