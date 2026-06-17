package se.tcmt.aurora.provider;

import com.intellij.openapi.diagnostic.Logger;
import se.tcmt.aurora.chat.ChatMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public interface AiProvider {

    Logger LOG = Logger.getInstance(AiProvider.class);

    String getName();

    boolean isConfigured(@NotNull ProviderConfig config);

    @Nullable
    String chat(@NotNull List<ChatMessage> history, @NotNull ProviderConfig config) throws Exception;

    /**
     * Stream chat response token-by-token via callback.
     * The callback receives each text delta as it arrives from the API.
     * Returns the full accumulated response on completion.
     */
    default String chatStream(@NotNull List<ChatMessage> history, @NotNull ProviderConfig config,
                              @NotNull java.util.function.Consumer<String> tokenCallback) throws Exception {
        // Default: fall back to non-streaming call (blocking)
        return chat(history, config);
    }

    void close() throws Exception;
}
