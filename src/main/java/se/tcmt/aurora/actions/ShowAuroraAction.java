package se.tcmt.aurora.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class ShowAuroraAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var toolWindow = ToolWindowManager.getInstance(e.getProject()).getToolWindow("Aurora");
        if (toolWindow != null) {
            toolWindow.show(() -> {});
        }
    }
}
