package se.tcmt.aurora.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Action to explain a selected piece of code.
 */
public class ExplainCodeAction extends BaseCodeAction {

    public ExplainCodeAction() {
        super("Explain Code", "EXPLAIN", "EXPLAIN");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        // Add icon or customize presentation if needed
    }
}
