package se.tcmt.aurora.services;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import se.tcmt.aurora.PluginBundle;

public class PluginService {
    private static final Logger LOG = Logger.getInstance(PluginService.class);
    private final Project project;

    public PluginService(@NotNull Project project) {
        this.project = project;
        PluginBundle bundle = new PluginBundle();
        LOG.info(bundle.getMessage("projectService", project.getName()));
        LOG.warn(
                "Don't forget to remove all non-needed sample code files with their corresponding registration entries in `plugin.xml`.");
    }

    public int getRandomNumber() {
        return (int) (Math.random() * 100) + 1;
    }
}
