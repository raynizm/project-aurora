package se.tcmt.aurora.toolwindow;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

public class PluginToolWindowFactory implements ToolWindowFactory {

    private static final Logger LOG = Logger.getInstance(PluginToolWindowFactory.class);

    public PluginToolWindowFactory() {
        LOG.warn(
                "Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        MyToolWindow myToolWindow = new MyToolWindow(toolWindow);
        JPanel contentPanel = myToolWindow.getContent();
        toolWindow.getContentManager().addContent(
                ContentFactory.getInstance().createContent(contentPanel, null, false)
        );
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    public static class MyToolWindow {

        private final Project project;

        public MyToolWindow(ToolWindow toolWindow) {
            this.project = toolWindow.getProject();
        }

        public JPanel getContent() {
            JBPanel<?> panel = new JBPanel<>(new BorderLayout());
            panel.setBorder(JBUI.Borders.empty(8));

            String htmlContent = "<html>" +
                                 "<head><title>Aurora Webview</title></head>" +
                                 "<body style='font-family: Arial, sans-serif; padding: 10px; margin: 0;'>" +
                                 "<h1>Project Aurora</h1>" +
                                 "<p>This is an HTML view inside the IntelliJ tool window.</p>" +
                                 "</body>" +
                                 "</html>";

            JEditorPane editorPane = new JEditorPane();
            editorPane.setContentType("text/html");
            editorPane.setEditable(false);
            editorPane.setText(htmlContent);

            JBScrollPane scrollPane = new JBScrollPane(editorPane);
            panel.add(scrollPane, BorderLayout.CENTER);

            return panel;
        }
    }
}
