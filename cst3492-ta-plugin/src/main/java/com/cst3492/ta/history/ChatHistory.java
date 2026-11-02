package com.cst3492.ta.history;

import com.cst3492.ta.model.KnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local conversation snapshots. Source text/vectors and credentials are never stored here. */
public final class ChatHistory {
    public int version = 1;
    public String activeSessionId;
    public List<Session> sessions = new ArrayList<>();

    public static final class Session {
        public String id = UUID.randomUUID().toString();
        public long updatedAt = System.currentTimeMillis();
        public String draft = "";
        public String draftCode;
        public boolean testTask;
        public String sourcePath = "";
        public String sourcePackage = "";
        public String codeSnapshot = "";
        public String selectedCode = "";
        public List<Turn> turns = new ArrayList<>();
        public String title() {
            if (testTask) return java.nio.file.Path.of(sourcePath).getFileName() + " · Unit tests";
            return turns.isEmpty() ? "New conversation" : turns.get(0).question.replaceAll("\\s+", " ");
        }
        @Override public String toString() { return title(); }
    }

    public static final class Turn {
        public String id = UUID.randomUUID().toString();
        public String question = "";
        public String code;
        public String status = "pending";
        public String answer = "";
        public boolean grounded;
        public List<Source> sources = new ArrayList<>();
        public String testCode = "";
        public String targetPath = "";
        public String filePath = "";
    }

    public record Source(String docId, String docName, int pageFrom, int pageTo) {
        public static Source from(KnowledgeChunk chunk) {
            return new Source(chunk.docId, chunk.docName, chunk.pageFrom, chunk.pageTo);
        }
        public KnowledgeChunk chunk() {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.docId = docId; chunk.docName = docName;
            chunk.pageFrom = pageFrom; chunk.pageTo = pageTo;
            return chunk;
        }
    }

    public Session active() {
        return sessions.stream().filter(s -> s.id.equals(activeSessionId)).findFirst()
                .orElseGet(this::newSession);
    }

    public Session newSession() {
        Session session = new Session();
        sessions.add(0, session);
        activeSessionId = session.id;
        return session;
    }

    /** A crashed/closed IDE must not reopen an endless spinner or re-send a request. */
    public void recoverInterrupted() {
        for (Session session : sessions) for (Turn turn : session.turns) {
            if ("pending".equals(turn.status)) {
                turn.status = "interrupted";
                turn.answer = "The IDE closed before this request finished. Send the question again to retry.";
            }
        }
    }
}
