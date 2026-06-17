package se.tcmt.aurora.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persistent settings for Aurora plugin.
 * Note: API key is stored in plain text in the IDE settings file.
 */
@State(
    name = "auroraSettings",
    storages = @Storage("aurora.xml")
)
public class AuroraSettingsState implements PersistentStateComponent<AuroraSettingsState> {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 4096;

    @Nullable
    private String apiKey;
    
    private String baseUrl = DEFAULT_BASE_URL;
    private String model = DEFAULT_MODEL;
    private double temperature = DEFAULT_TEMPERATURE;
    private int maxTokens = DEFAULT_MAX_TOKENS;

    @Nullable
    public static AuroraSettingsState getInstance() {
        return com.intellij.openapi.components.ServiceManager.getService(AuroraSettingsState.class);
    }

    @Nullable
    @Override
    public AuroraSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AuroraSettingsState state) {
        com.intellij.util.xmlb.XmlSerializerUtil.copyBean(state, this);
    }

    // Getters and setters
    @Nullable
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = Math.max(0.0, Math.min(2.0, temperature));
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = Math.max(256, Math.min(8192, maxTokens));
    }

    /**
     * Converts settings to ProviderConfig for use by AI providers.
     */
    @NotNull
    public se.tcmt.aurora.provider.ProviderConfig toProviderConfig() {
        se.tcmt.aurora.provider.ProviderConfig config = new se.tcmt.aurora.provider.ProviderConfig();
        config.setApiKey(apiKey);
        config.setBaseUrl(baseUrl);
        config.setModel(model);
        config.setTemperature(temperature);
        config.setMaxTokens(maxTokens);
        return config;
    }

    /**
     * Updates settings from a ProviderConfig.
     */
    public void fromProviderConfig(@NotNull se.tcmt.aurora.provider.ProviderConfig config) {
        if (config.getApiKey() != null) {
            setApiKey(config.getApiKey());
        }
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            setBaseUrl(config.getBaseUrl());
        }
        if (config.getModel() != null && !config.getModel().isEmpty()) {
            setModel(config.getModel());
        }
        if (config.getTemperature() >= 0.0) {
            setTemperature(config.getTemperature());
        }
        if (config.getMaxTokens() > 0) {
            setMaxTokens(config.getMaxTokens());
        }
    }
}
