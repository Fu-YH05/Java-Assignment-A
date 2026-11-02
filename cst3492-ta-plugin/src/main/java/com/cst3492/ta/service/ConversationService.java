package com.cst3492.ta.service;

import com.cst3492.ta.model.Conversation;
import com.cst3492.ta.util.JsonUtil;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/** Immutable JSON snapshots avoid sharing mutable Swing-owned lists with the IDE save thread. */
@Service(Service.Level.PROJECT)
@State(name = "CstTaConversations", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ConversationService implements PersistentStateComponent<ConversationService.Snapshot> {
    public static final class Snapshot {
        public String json = "";
    }

    private volatile Snapshot snapshot = new Snapshot();

    @Override public @NotNull Snapshot getState() { return snapshot; }
    @Override public void loadState(@NotNull Snapshot state) { snapshot = state; }

    public Conversation.History read() throws IOException {
        Conversation.History history = snapshot.json.isBlank() ? new Conversation.History()
                : JsonUtil.mapper().readValue(snapshot.json, Conversation.History.class);
        if (history == null || history.conversations == null) throw new IOException("Invalid conversation history");
        history.recoverInterruptedRequests();
        return history;
    }

    public void save(Conversation.History history) throws IOException {
        // ponytail: snapshot the full history per edit; debounce if large histories make typing slow.
        Snapshot next = new Snapshot();
        next.json = JsonUtil.mapper().writeValueAsString(history);
        snapshot = next;
    }
}
