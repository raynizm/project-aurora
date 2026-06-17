package se.tcmt.aurora.chat;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.util.ArrayList;
import java.util.List;

public class AuroraChatPanel extends JPanel {

    private static final Logger LOG = Logger.getInstance(AuroraChatPanel.class);

    private final JTextArea messageDisplay;
    private final JTextField inputField;
    private final JButton sendButton;
    private final JLabel statusLabel;
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private volatile boolean isProcessing = false;

    // Provider and config injected by tool window factory
    private volatile se.tcmt.aurora.provider.AiProvider activeProvider;
    private volatile se.tcmt.aurora.provider.ProviderConfig providerConfig;
    
    // Project reference for context extraction
    private final Project project;
    private volatile Editor currentEditor;

    public AuroraChatPanel(@NotNull Project project) {
        this.project = project;
        setLayout(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));

        // Message display area
        messageDisplay = new JTextArea();
        messageDisplay.setEditable(false);
        messageDisplay.setFont(new Font("Monospaced", Font.PLAIN, 13));
        messageDisplay.setLineWrap(true);
        messageDisplay.setWrapStyleWord(true);
        messageDisplay.setBackground(new Color(26, 30, 35));
        messageDisplay.setForeground(new Color(204, 204, 204));

        JBScrollPane scrollPane = new JBScrollPane(messageDisplay);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // Input area panel
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBackground(new Color(31, 35, 40));
        inputPanel.setBorder(JBUI.Borders.empty(8, 0, 0, 0));

        inputField = new JTextField();
        inputField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        inputField.setBackground(new Color(45, 50, 57));
        inputField.setForeground(Color.WHITE);
        inputField.setBorder(JBUI.Borders.empty(8, 12));

        // Enter key sends message
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("SansSerif", Font.PLAIN, 12));
        sendButton.setBackground(new Color(0, 150, 255));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorderPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.addActionListener(e -> sendMessage());

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);

        // Status label
        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(new Color(120, 130, 140));
        statusLabel.setBorder(JBUI.Borders.empty(4, 8));
        add(statusLabel, BorderLayout.NORTH);

        // Welcome message
        appendMessage(ChatMessage.Role.ASSISTANT, "Welcome to Aurora! I'm your AI coding assistant. How can I help you today?");
    }

    /**
     * Set the current editor for context extraction.
     * Called by the tool window when editor changes.
     */
    public void setCurrentEditor(@Nullable Editor editor) {
        this.currentEditor = editor;
    }

    public void setActiveProvider(se.tcmt.aurora.provider.AiProvider provider) {
        this.activeProvider = provider;
    }

    public void setProviderConfig(se.tcmt.aurora.provider.ProviderConfig config) {
        this.providerConfig = config;
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || isProcessing) return;

        inputField.setText("");
        
        // Extract context from current editor
        se.tcmt.aurora.context.CodeContextExtractor.Context context = 
            se.tcmt.aurora.context.CodeContextExtractor.extractContext(project, currentEditor);
        
        // Prepend context to user message if available
        String fullMessage = text;
        if (context.hasContext()) {
            fullMessage = context.formatForPrompt() + text;
            LOG.debug("Added code context to message");
        }

        appendMessage(ChatMessage.Role.USER, text);
        chatHistory.add(new ChatMessage(ChatMessage.Role.USER, fullMessage));

        // Show loading state
        isProcessing = true;
        sendButton.setEnabled(false);
        statusLabel.setText("Aurora is thinking...");

        // Send to AI provider in background thread
        new Thread(() -> {
            try {
                if (providerConfig == null || !providerConfig.hasApiKey()) {
                    SwingUtilities.invokeLater(() -> {
                        appendMessage(ChatMessage.Role.ASSISTANT, "Please configure your API key in Settings > Tools > Aurora.");
                        statusLabel.setText("Ready");
                        isProcessing = false;
                        sendButton.setEnabled(true);
                    });
                    return;
                }

                if (activeProvider == null) {
                    SwingUtilities.invokeLater(() -> {
                        appendMessage(ChatMessage.Role.ASSISTANT, "No AI provider configured.");
                        statusLabel.setText("Ready");
                        isProcessing = false;
                        sendButton.setEnabled(true);
                    });
                    return;
                }

                String response = activeProvider.chat(chatHistory, providerConfig);

                SwingUtilities.invokeLater(() -> {
                    if (response != null && !response.isEmpty()) {
                        appendMessage(ChatMessage.Role.ASSISTANT, response);
                        chatHistory.add(new ChatMessage(ChatMessage.Role.ASSISTANT, response));
                    } else {
                        appendMessage(ChatMessage.Role.ASSISTANT, "Sorry, I couldn't get a response. Please check your API key and try again.");
                    }
                    statusLabel.setText("Ready");
                    isProcessing = false;
                    sendButton.setEnabled(true);
                });
            } catch (Exception e) {
                LOG.error("Error calling AI provider: " + e.getMessage());
                SwingUtilities.invokeLater(() -> {
                    appendMessage(ChatMessage.Role.ASSISTANT, "Error: " + e.getMessage());
                    statusLabel.setText("Ready");
                    isProcessing = false;
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void appendMessage(@NotNull ChatMessage.Role role, @NotNull String content) {
        SwingUtilities.invokeLater(() -> {
            String prefix = role == ChatMessage.Role.USER ? "> " : "Aurora: ";
            Color color = role == ChatMessage.Role.USER ? new Color(100, 180, 255) : new Color(204, 204, 204);

            messageDisplay.append(prefix + content + "\n\n");
            messageDisplay.setCaretPosition(messageDisplay.getDocument().getLength());
        });
    }

    public void clearChat() {
        chatHistory.clear();
        SwingUtilities.invokeLater(() -> messageDisplay.setText(""));
        appendMessage(ChatMessage.Role.ASSISTANT, "Chat cleared. How can I help you?");
    }
}
