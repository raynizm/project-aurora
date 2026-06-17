package se.tcmt.aurora.provider;

import com.intellij.openapi.diagnostic.Logger;
import se.tcmt.aurora.chat.ChatMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OpenAiProvider implements AiProvider {

    @Override
    public String getName() {
        return "OpenAI";
    }

    @Override
    public boolean isConfigured(@NotNull ProviderConfig config) {
        return config.hasApiKey();
    }

    @Nullable
    @Override
    public String chat(@NotNull List<ChatMessage> history, @NotNull ProviderConfig config) throws Exception {
        if (!isConfigured(config)) {
            LOG.warn("OpenAI provider not configured (missing API key)");
            return null;
        }

        // Build messages array for OpenAI API
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"model\":\"").append(escapeJson(config.getModel())).append("\"");
        jsonBuilder.append(",\"messages\":[");

        for (int i = 0; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            if (i > 0) jsonBuilder.append(",");
            jsonBuilder.append("{\"role\":\"").append(msg.getRole().name().toLowerCase()).append("\"");
            jsonBuilder.append(",\"content\":\"").append(escapeJson(msg.getContent())).append("\"}");
        }

        jsonBuilder.append("]");
        jsonBuilder.append(",\"temperature\":").append(config.getTemperature());
        jsonBuilder.append(",\"max_tokens\":").append(config.getMaxTokens());
        jsonBuilder.append("}");

        String requestBody = jsonBuilder.toString();
        LOG.info("OpenAI request: " + requestBody.substring(0, Math.min(200, requestBody.length())));

        // Send HTTP POST request
        URL url = new URL(config.getBaseUrl() + "/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                String error = readStream(conn.getErrorStream());
                LOG.error("OpenAI API error: " + responseCode + " - " + error);
                return null;
            }

            String response = readStream(conn.getInputStream());
            // Parse the response to extract the assistant's message content
            return parseAssistantMessage(response);
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public void close() throws Exception {
        // No resources to close for HTTP connections (they're disconnected in chat())
    }

    private String readStream(@Nullable java.io.InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @Nullable
    private String parseAssistantMessage(@NotNull String jsonResponse) {
        try {
            // Simple JSON parsing: find "content":"..." in the response
            int contentStart = jsonResponse.indexOf("\"content\":\"");
            if (contentStart == -1) return null;
            contentStart += 11; // skip past "\"content\":"

            StringBuilder result = new StringBuilder();
            boolean escape = false;
            for (int i = contentStart; i < jsonResponse.length(); i++) {
                char c = jsonResponse.charAt(i);
                if (escape) {
                    switch (c) {
                        case '"':  result.append('"'); break;
                        case '\\': result.append('\\'); break;
                        case 'n':  result.append('\n'); break;
                        case 't':  result.append('\t'); break;
                        case '/':  result.append('/'); break;
                        default:   result.append(c); break;
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    break; // end of content string
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        } catch (Exception e) {
            LOG.error("Failed to parse OpenAI response: " + e.getMessage());
            return null;
        }
    }

    private String escapeJson(@NotNull String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
