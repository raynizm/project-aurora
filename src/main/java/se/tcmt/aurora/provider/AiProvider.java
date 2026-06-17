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

    void close() throws Exception;
}
