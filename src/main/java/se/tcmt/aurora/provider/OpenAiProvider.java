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

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000L; // 1 second
    private static final long MAX_RETRY_DELAY_MS = 30000L; // 30 seconds

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
            throw new IllegalStateException("API key not configured. Please set it in Settings > Tools > Aurora.");
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

        // Retry loop with exponential backoff
        Exception lastException = null;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String response = sendRequest(config.getBaseUrl() + "/chat/completions", requestBody, config);
                
                if (response != null) {
                    return parseAssistantMessage(response);
                }
                
                // If we got a null response but no exception, treat as transient error
                throw new RuntimeException("Empty response from API");
                
            } catch (Exception e) {
                lastException = e;
                LOG.warn("OpenAI API attempt " + attempt + "/" + MAX_RETRIES + " failed: " + e.getMessage());
                
                // Check if we should retry (transient errors only)
                if (!shouldRetry(e) || attempt == MAX_RETRIES) {
                    break;
                }
                
                // Wait before retrying with exponential backoff
                try {
                    Thread.sleep(Math.min(delayMs, MAX_RETRY_DELAY_MS));
                    delayMs *= 2; // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Request interrupted", ie);
                }
            }
        }

        // All retries exhausted
        LOG.error("OpenAI API failed after " + MAX_RETRIES + " attempts");
        throw new RuntimeException("Failed to get response from AI provider: " + lastException.getMessage());
    }

    private boolean shouldRetry(@NotNull Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        
        // Retry on transient errors
        return msg.contains("429") ||  // Rate limit
               msg.contains("500") ||  // Server error
               msg.contains("503") ||  // Service unavailable
               msg.contains("timeout") ||
               msg.contains("connection") ||
               msg.contains("network");
    }

    private String sendRequest(@NotNull String urlString, @NotNull String requestBody, @NotNull ProviderConfig config) throws Exception {
        URL url = new URL(urlString);
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
            
            // Handle non-200 responses with specific error messages
            if (responseCode == 401) {
                throw new RuntimeException("Authentication failed. Please check your API key.");
            } else if (responseCode == 429) {
                throw new RuntimeException("Rate limit exceeded. Please wait a moment and try again.");
            } else if (responseCode >= 500) {
                String error = readStream(conn.getErrorStream());
                throw new RuntimeException("Server error (" + responseCode + "): " + error);
            } else if (responseCode != 200) {
                String error = readStream(conn.getErrorStream());
                throw new RuntimeException("API error (" + responseCode + "): " + error);
            }

            return readStream(conn.getInputStream());
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

    /**
     * Extract content delta from an SSE data line (OpenAI streaming format).
     * Data lines look like: {"choices":[{"delta":{"content":"Hello"}}]}
     * or: [DONE]
     */
    @Nullable
    private String parseSseDelta(@NotNull String dataLine) {
        if ("[DONE]".equals(dataLine.trim())) {
            return null; // Stream complete
        }

        try {
            // Find the first "content" field inside a delta object
            int contentKey = dataLine.indexOf("\"content\":\"");
            if (contentKey == -1) {
                // Some providers may use "text" instead of "content", or delta may be empty
                return null;
            }

            int valueStart = contentKey + 11; // skip past "\"content\":"
            StringBuilder result = new StringBuilder();
            boolean escape = false;
            for (int i = valueStart; i < dataLine.length(); i++) {
                char c = dataLine.charAt(i);
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
            return result.length() > 0 ? result.toString() : null;
        } catch (Exception e) {
            LOG.warn("Failed to parse SSE delta: " + dataLine);
            return null;
        }
    }

    @Override
    public String chatStream(@NotNull List<ChatMessage> history, @NotNull ProviderConfig config,
                             @NotNull java.util.function.Consumer<String> tokenCallback) throws Exception {
        if (!isConfigured(config)) {
            LOG.warn("OpenAI provider not configured (missing API key)");
            throw new IllegalStateException("API key not configured. Please set it in Settings > Tools > Aurora.");
        }

        // Build messages array for OpenAI API (same as chat())
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
        // Enable streaming mode
        jsonBuilder.append(",\"stream\":true");
        jsonBuilder.append("}");

        String requestBody = jsonBuilder.toString();
        LOG.info("OpenAI streaming request: " + requestBody.substring(0, Math.min(200, requestBody.length())));

        Exception lastException = null;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String fullResponse = sendStreamingRequest(config.getBaseUrl() + "/chat/completions", requestBody, config, tokenCallback);
                return fullResponse;

            } catch (Exception e) {
                lastException = e;
                LOG.warn("OpenAI API streaming attempt " + attempt + "/" + MAX_RETRIES + " failed: " + e.getMessage());

                if (!shouldRetry(e) || attempt == MAX_RETRIES) {
                    break;
                }

                try {
                    Thread.sleep(Math.min(delayMs, MAX_RETRY_DELAY_MS));
                    delayMs *= 2;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Request interrupted", ie);
                }
            }
        }

        LOG.error("OpenAI API failed after " + MAX_RETRIES + " attempts");
        throw new RuntimeException("Failed to get response from AI provider: " + lastException.getMessage());
    }

    /**
     * Send an HTTP POST request with streaming (SSE) response reading.
     * Reads line-by-line, parses SSE data: lines, and calls tokenCallback for each content delta.
     */
    private String sendStreamingRequest(@NotNull String urlString, @NotNull String requestBody,
                                        @NotNull ProviderConfig config,
                                        @NotNull java.util.function.Consumer<String> tokenCallback) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + config.getApiKey());
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();

            if (responseCode == 401) {
                throw new RuntimeException("Authentication failed. Please check your API key.");
            } else if (responseCode == 429) {
                throw new RuntimeException("Rate limit exceeded. Please wait a moment and try again.");
            } else if (responseCode >= 500) {
                String error = readStream(conn.getErrorStream());
                throw new RuntimeException("Server error (" + responseCode + "): " + error);
            } else if (responseCode != 200) {
                String error = readStream(conn.getErrorStream());
                throw new RuntimeException("API error (" + responseCode + "): " + error);
            }

            // Read SSE stream line-by-line
            StringBuilder fullResponse = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String dataPayload = line.substring(6); // strip "data: " prefix

                        String delta = parseSseDelta(dataPayload);
                        if (delta != null && !delta.isEmpty()) {
                            fullResponse.append(delta);
                            tokenCallback.accept(delta);
                        }
                    }
                }
            }

            return fullResponse.toString();
        } finally {
            conn.disconnect();
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
