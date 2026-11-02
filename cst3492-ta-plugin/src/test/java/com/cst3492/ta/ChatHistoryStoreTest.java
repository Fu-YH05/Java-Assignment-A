package com.cst3492.ta;

import com.cst3492.ta.history.ChatHistory;
import com.cst3492.ta.history.ChatHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryStoreTest {
    @TempDir Path directory;

    @Test void roundTripRetainsCodeDraftCitationsAndIndependentSnapshot() throws Exception {
        var state = new ChatHistory();
        var session = state.active();
        session.draft = "下一条问题";
        session.draftCode = "class Draft {}";
        var turn = new ChatHistory.Turn();
        turn.question = "第一个问题";
        turn.code = "class Bean {}";
        turn.status = "answered";
        turn.answer = "Answer [课程.pdf, pp.2-3]";
        turn.grounded = true;
        turn.sources.add(new ChatHistory.Source("课程", "课程.pdf", 2, 3));
        session.turns.add(turn);
        var snapshot = ChatHistoryStore.snapshot(state);
        turn.answer = "Later edit";
        var store = new ChatHistoryStore(directory.resolve("project/history.json"));
        store.save(snapshot);
        var restored = store.load();
        assertEquals("下一条问题", restored.active().draft);
        assertEquals("class Draft {}", restored.active().draftCode);
        var savedTurn = restored.active().turns.get(0);
        assertEquals("class Bean {}", savedTurn.code);
        assertEquals("Answer [课程.pdf, pp.2-3]", savedTurn.answer);
        assertEquals("课程.pdf, pp.2-3", savedTurn.sources.get(0).chunk().citationLabel());
        assertTrue(savedTurn.grounded);
    }

    @Test void reopenedPendingRequestIsInterruptedAndOtherStatusesArePreserved() throws Exception {
        var state = new ChatHistory();
        for (String status : new String[]{"pending", "failed", "cancelled", "answered"}) {
            var turn = new ChatHistory.Turn(); turn.status = status;
            state.active().turns.add(turn);
        }
        var store = new ChatHistoryStore(directory.resolve("history.json"));
        store.save(state);
        var turns = store.load().active().turns;
        assertEquals("interrupted", turns.get(0).status);
        assertFalse(turns.get(0).answer.isBlank());
        assertEquals("failed", turns.get(1).status);
        assertEquals("cancelled", turns.get(2).status);
        assertEquals("answered", turns.get(3).status);
    }

    @Test void invalidFileIsNotSilentlyReplaced() throws Exception {
        Path file = directory.resolve("history.json");
        Files.writeString(file, "{broken");
        assertThrows(java.io.IOException.class, () -> new ChatHistoryStore(file).load());
        assertEquals("{broken", Files.readString(file));
        Files.writeString(file, "{\"version\":99,\"sessions\":[]}");
        assertThrows(java.io.IOException.class, () -> new ChatHistoryStore(file).load());
    }

    @Test void projectFilesStaySeparateAndDeletingSessionsPersists() throws Exception {
        var first = new ChatHistoryStore(directory.resolve("first.json"));
        var second = new ChatHistoryStore(directory.resolve("second.json"));
        var state = new ChatHistory(); state.newSession(); state.newSession();
        first.save(state);
        assertEquals(2, first.load().sessions.size());
        assertTrue(second.load().sessions.isEmpty());
        state.sessions.remove(0); state.activeSessionId = state.sessions.get(0).id;
        first.save(state);
        assertEquals(1, first.load().sessions.size());
        try (var paths = Files.list(directory)) {
            assertFalse(paths.anyMatch(p -> p.toString().endsWith(".tmp")));
        }
    }
}
