package se.tcmt.aurora.provider;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "AuroraProviderConfig", storages = @Storage("aurora-config.xml"))
public class ProviderConfig {

    private String apiKey = "";
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";
    private double temperature = 0.7;
    private int maxTokens = 4096;

    public String getApiKey() { return apiKey; }
    public void setApiKey(@Nullable String apiKey) { this.apiKey = apiKey != null ? apiKey : ""; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(@NotNull String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(@NotNull String model) { this.model = model; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public boolean hasApiKey() { return apiKey != null && !apiKey.isEmpty(); }
}
