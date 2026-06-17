package se.tcmt.aurora.settings;

import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
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

    private static final String KEY_STORE_SERVICE = "Aurora.APIKey";
    
    // Settings that are NOT encrypted (stored in XML)
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

    // API Key methods - uses PasswordSafe for secure storage
    public void setApiKey(@NotNull String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            PasswordSafe.getInstance().getCredentials(KEY_STORE_SERVICE, KEY_STORE_SERVICE);
            PasswordSafe.getInstance().setCredentials(new Credentials(KEY_STORE_SERVICE, ""));
        } else {
            PasswordSafe.getInstance().setCredentials(new Credentials(KEY_STORE_SERVICE, apiKey));
        }
    }

    public @NotNull String getApiKey() {
        Credentials creds = PasswordSafe.getInstance().getCredentials(KEY_STORE_SERVICE, KEY_STORE_SERVICE);
        return creds != null ? creds.getPasswordAsString() : "";
    }

    public boolean hasApiKey() {
        return !getApiKey().isEmpty();
    }

    // Base URL
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(@NotNull String baseUrl) { this.baseUrl = baseUrl; }

    // Model
    public String getModel() { return model; }
    public void setModel(@NotNull String model) { this.model = model; }

    // Temperature (0.0 - 2.0)
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { 
        if (temperature < 0.0) temperature = 0.0;
        if (temperature > 2.0) temperature = 2.0;
        this.temperature = temperature; 
    }

    // Max Tokens (256 - 8192)
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { 
        if (maxTokens < 256) maxTokens = 256;
        if (maxTokens > 8192) maxTokens = 8192;
        this.maxTokens = maxTokens; 
    }

    // Convert to provider config format
    public se.tcmt.aurora.provider.ProviderConfig toProviderConfig() {
        se.tcmt.aurora.provider.ProviderConfig config = new se.tcmt.aurora.provider.ProviderConfig();
        config.setApiKey(getApiKey());
        config.setBaseUrl(baseUrl);
        config.setModel(model);
        config.setTemperature(temperature);
        config.setMaxTokens(maxTokens);
        return config;
    }

    // Validate settings
    public boolean isValid() {
        return hasApiKey() && !baseUrl.isEmpty() && !model.isEmpty();
    }

    public String getValidationMessage() {
        if (!hasApiKey()) {
            return "API key is required";
        }
        if (baseUrl.isEmpty()) {
            return "Base URL is required";
        }
        if (model.isEmpty()) {
            return "Model name is required";
        }
        return null; // Valid
    }
}
