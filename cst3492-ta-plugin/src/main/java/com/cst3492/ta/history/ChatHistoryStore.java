package com.cst3492.ta.history;

import com.cst3492.ta.util.JsonUtil;
import java.io.IOException;
import java.nio.file.*;

/** Call from a serial background queue; replacement never destroys the previous valid snapshot. */
public final class ChatHistoryStore {
    private final Path file;
    public ChatHistoryStore(Path file) { this.file = file; }

    public ChatHistory load() throws IOException {
        if (!Files.exists(file)) return new ChatHistory();
        ChatHistory history = JsonUtil.mapper().readValue(file.toFile(), ChatHistory.class);
        if (history == null || history.version != 1 || history.sessions == null) {
            throw new IOException("Unsupported or invalid chat history. The saved file was left untouched.");
        }
        for (var session : history.sessions) {
            if (session == null || session.id == null || session.turns == null || session.draft == null)
                throw new IOException("Invalid conversation in history.");
            for (var turn : session.turns) {
                if (turn == null || turn.id == null || turn.question == null || turn.status == null
                        || turn.answer == null || turn.sources == null
                        || turn.sources.stream().anyMatch(s -> s == null || s.docId() == null || s.docName() == null))
                    throw new IOException("Invalid turn in chat history.");
            }
        }
        history.recoverInterrupted();
        return history;
    }

    /** Detached copy made on the UI thread before a background save can observe later edits. */
    public static ChatHistory snapshot(ChatHistory history) {
        return JsonUtil.mapper().convertValue(history, ChatHistory.class);
    }

    public void save(ChatHistory history) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "chat-history-", ".tmp");
        try {
            JsonUtil.mapper().writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), history);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
