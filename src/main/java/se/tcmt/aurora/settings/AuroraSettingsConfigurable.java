package se.tcmt.aurora.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AuroraSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private JPasswordField apiKeyField;
    private JTextField baseUrlField;
    private JTextField modelField;
    private JSpinner temperatureSpinner;
    private JSpinner maxTokensSpinner;
    private JButton testButton;
    private JLabel statusLabel;

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

        // Test Connection Button
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.CENTER; gbc.weightx = 1.0;
        testButton = new JButton("Test Connection");
        testButton.addActionListener(e -> testConnection());
        mainPanel.add(testButton, gbc);

        // Status Label
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 1.0;
        statusLabel = new JLabel("");
        statusLabel.setForeground(new Color(120, 130, 140));
        mainPanel.add(statusLabel, gbc);

        // Info label
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 1.0;
        JLabel infoLabel = new JLabel("<html><font color='#888'>Aurora uses the OpenAI-compatible API format.<br/>" +
            "For other providers (Anthropic, local models), set a compatible base URL.</font></html>");
        mainPanel.add(infoLabel, gbc);

        return mainPanel;
    }

    private void testConnection() {
        testButton.setEnabled(false);
        statusLabel.setText("Testing connection...");
        statusLabel.setForeground(new Color(120, 130, 140));

        new Thread(() -> {
            try {
                se.tcmt.aurora.provider.ProviderConfig config = createProviderConfig();
                
                if (!config.hasApiKey()) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Error: API key is required");
                        statusLabel.setForeground(Color.RED);
                        testButton.setEnabled(true);
                    });
                    return;
                }

                // Create a temporary provider and test
                se.tcmt.aurora.provider.AiProvider provider = new se.tcmt.aurora.provider.OpenAiProvider();
                
                // Send a minimal request to test connectivity
                java.util.List<se.tcmt.aurora.chat.ChatMessage> testMessages = new java.util.ArrayList<>();
                testMessages.add(new se.tcmt.aurora.chat.ChatMessage(se.tcmt.aurora.chat.ChatMessage.Role.USER, "Test"));
                
                String response = provider.chat(testMessages, config);
                
                SwingUtilities.invokeLater(() -> {
                    if (response != null) {
                        statusLabel.setText("Connection successful! Response received.");
                        statusLabel.setForeground(new Color(80, 200, 120));
                        Messages.showInfoMessage("Test connection successful!\nThe AI provider is responding correctly.", "Aurora Connection Test");
                    } else {
                        statusLabel.setText("Error: No response from API");
                        statusLabel.setForeground(Color.RED);
                        Messages.showErrorDialog("The API did not return a valid response. Check your settings and try again.", "Aurora Connection Test");
                    }
                    testButton.setEnabled(true);
                });
                
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                    Messages.showErrorDialog("Connection failed: " + e.getMessage(), "Aurora Connection Test");
                    testButton.setEnabled(true);
                });
            }
        }).start();
    }

    private se.tcmt.aurora.provider.ProviderConfig createProviderConfig() {
        se.tcmt.aurora.provider.ProviderConfig config = new se.tcmt.aurora.provider.ProviderConfig();
        char[] pwd = apiKeyField.getPassword();
        config.setApiKey(new String(pwd));
        config.setBaseUrl(baseUrlField.getText());
        config.setModel(modelField.getText());
        config.setTemperature(((Double) temperatureSpinner.getValue()).doubleValue());
        config.setMaxTokens((Integer) maxTokensSpinner.getValue());
        return config;
    }

    @Override
    public boolean isModified() {
        AuroraSettingsState settings = AuroraSettingsState.getInstance();
        char[] pwd = apiKeyField.getPassword();
        String key = new String(pwd);
        return !key.equals(settings.getApiKey())
            || !baseUrlField.getText().equals(settings.getBaseUrl())
            || !modelField.getText().equals(settings.getModel())
            || ((Double) temperatureSpinner.getValue()).doubleValue() != settings.getTemperature()
            || ((Integer) maxTokensSpinner.getValue()).intValue() != settings.getMaxTokens();
    }

    @Override
    public void apply() {
        // Validate before applying
        char[] pwd = apiKeyField.getPassword();
        String key = new String(pwd);
        
        if (key.isEmpty()) {
            int result = Messages.showYesNoDialog(
                "You are clearing the API key. Are you sure?",
                "Aurora Settings",
                Messages.getQuestionIcon()
            );
            if (result != Messages.YES) {
                return;
            }
        }

        AuroraSettingsState settings = AuroraSettingsState.getInstance();
        settings.setApiKey(key);
        settings.setBaseUrl(baseUrlField.getText());
        settings.setModel(modelField.getText());
        settings.setTemperature(((Double) temperatureSpinner.getValue()).doubleValue());
        settings.setMaxTokens((Integer) maxTokensSpinner.getValue());
        
        statusLabel.setText("Settings saved successfully");
        statusLabel.setForeground(new Color(80, 200, 120));
    }

    @Override
    public void reset() {
        AuroraSettingsState settings = AuroraSettingsState.getInstance();
        apiKeyField.setText(settings.getApiKey());
        baseUrlField.setText(settings.getBaseUrl());
        modelField.setText(settings.getModel());
        temperatureSpinner.setValue(settings.getTemperature());
        maxTokensSpinner.setValue(settings.getMaxTokens());
        statusLabel.setText("");
    }

    @Override
    public void disposeUIResources() {
        mainPanel = null;
    }
}
