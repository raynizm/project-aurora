package se.tcmt.aurora.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AuroraSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JTextField apiKeyField;
    private JTextField baseUrlField;
    private JTextField modelField;
    private JSpinner temperatureSpinner;
    private JSpinner maxTokensSpinner;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Aurora";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.weightx = 1.0;

        int row = 0;

        // API Key
        gbc.gridx = 0; gbc.gridy = row; gbc.anchor = GridBagConstraints.WEST;
        mainPanel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        apiKeyField = new JPasswordField(30);
        mainPanel.add(apiKeyField, gbc);

        // Base URL
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0;
        mainPanel.add(new JLabel("Base URL:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        baseUrlField = new JTextField(30);
        baseUrlField.setText("https://api.openai.com/v1");
        mainPanel.add(baseUrlField, gbc);

        // Model
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0;
        mainPanel.add(new JLabel("Model:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        modelField = new JTextField(30);
        modelField.setText("gpt-4o-mini");
        mainPanel.add(modelField, gbc);

        // Temperature
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0;
        mainPanel.add(new JLabel("Temperature (0.0 - 2.0):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        SpinnerNumberModel tempModel = new SpinnerNumberModel(0.7, 0.0, 2.0, 0.1);
        temperatureSpinner = new JSpinner(tempModel);
        mainPanel.add(temperatureSpinner, gbc);

        // Max Tokens
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0;
        mainPanel.add(new JLabel("Max Tokens:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        SpinnerNumberModel tokenModel = new SpinnerNumberModel(4096, 256, 8192, 256);
        maxTokensSpinner = new JSpinner(tokenModel);
        mainPanel.add(maxTokensSpinner, gbc);

        // Info label
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 1.0;
        JLabel infoLabel = new JLabel("<html><font color='#888'>Aurora uses the OpenAI-compatible API format.<br/>" +
            "For other providers (Anthropic, local models), set a compatible base URL.</font></html>");
        mainPanel.add(infoLabel, gbc);

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        AuroraSettingsState settings = AuroraSettingsState.getInstance();
        char[] pwd = ((JPasswordField) apiKeyField).getPassword();
        String key = new String(pwd);
        return !key.equals(settings.getApiKey())
            || !baseUrlField.getText().equals(settings.getBaseUrl())
            || !modelField.getText().equals(settings.getModel())
            || ((Double) temperatureSpinner.getValue()).doubleValue() != settings.getTemperature()
            || ((Integer) maxTokensSpinner.getValue()).intValue() != settings.getMaxTokens();
    }

    @Override
    public void apply() {
        AuroraSettingsState settings = AuroraSettingsState.getInstance();
        char[] pwd = ((JPasswordField) apiKeyField).getPassword();
        settings.setApiKey(new String(pwd));
        settings.setBaseUrl(baseUrlField.getText());
        settings.setModel(modelField.getText());
        settings.setTemperature(((Double) temperatureSpinner.getValue()).doubleValue());
        settings.setMaxTokens((Integer) maxTokensSpinner.getValue());
    }

    @Override
    public void reset() {
        AuroraSettingsState settings = AuroraSettingsState.getInstance();
        apiKeyField.setText(settings.getApiKey());
        baseUrlField.setText(settings.getBaseUrl());
        modelField.setText(settings.getModel());
        temperatureSpinner.setValue(settings.getTemperature());
        maxTokensSpinner.setValue(settings.getMaxTokens());
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
    }
}
