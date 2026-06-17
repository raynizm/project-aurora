package se.tcmt.aurora.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "AuroraSettings",
    storages = @Storage("aurora-settings.xml")
)
public class AuroraSettingsState implements PersistentStateComponent<AuroraSettingsState> {

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    private double temperature = 0.7;
    private int maxTokens = 4096;

    @Nullable
    @Override
    public AuroraSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AuroraSettingsState state) {
        com.intellij.util.xmlb.XmlSerializerUtil.copyBean(state, this);
    }

    public static @NotNull AuroraSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(AuroraSettingsState.class);
    }

    // Getters and setters for UI binding
    public String getApiKey() { return apiKey; }
    public void setApiKey(@NotNull String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(@NotNull String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(@NotNull String model) { this.model = model; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public boolean hasApiKey() { return apiKey != null && !apiKey.isEmpty(); }

    // Convert to provider config format
    public se.tcmt.aurora.provider.ProviderConfig toProviderConfig() {
        se.tcmt.aurora.provider.ProviderConfig config = new se.tcmt.aurora.provider.ProviderConfig();
        config.setApiKey(apiKey);
        config.setBaseUrl(baseUrl);
        config.setModel(model);
        config.setTemperature(temperature);
        config.setMaxTokens(maxTokens);
        return config;
    }
}
