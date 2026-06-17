package se.tcmt.aurora.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;
import se.tcmt.aurora.chat.AuroraChatPanel;

public class AuroraToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        AuroraChatPanel chatPanel = new AuroraChatPanel(project);

        // Inject provider and config into the panel
        se.tcmt.aurora.provider.OpenAiProvider openAiProvider = new se.tcmt.aurora.provider.OpenAiProvider();
        se.tcmt.aurora.settings.AuroraSettingsState settings = se.tcmt.aurora.settings.AuroraSettingsState.getInstance();
        chatPanel.setActiveProvider(openAiProvider);
        chatPanel.setProviderConfig(settings.toProviderConfig());

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(chatPanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
