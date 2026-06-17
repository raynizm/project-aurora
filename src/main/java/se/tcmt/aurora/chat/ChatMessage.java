package se.tcmt.aurora.chat;

import com.intellij.openapi.diagnostic.Logger;

public class ChatMessage {

    private static final Logger LOG = Logger.getInstance(ChatMessage.class);

    public enum Role {
        USER, ASSISTANT, SYSTEM
    }

    private final Role role;
    private final String content;
    private final long timestamp;

    public ChatMessage(Role role, String content) {
        this.role = role;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
    }

    public Role getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + role.name() + "] " + content.substring(0, Math.min(50, content.length()));
    }
}
