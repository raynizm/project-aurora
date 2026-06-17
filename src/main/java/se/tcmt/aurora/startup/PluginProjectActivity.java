package se.tcmt.aurora.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public class PluginProjectActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(PluginProjectActivity.class);

    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        LOG.warn(
                "Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
        return Unit.INSTANCE;
    }
}
