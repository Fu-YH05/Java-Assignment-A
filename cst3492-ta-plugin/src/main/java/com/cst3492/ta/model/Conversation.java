package com.cst3492.ta.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Project-local transcript. Keeping a transcript does not add model conversation memory. */
public final class Conversation {
    public String id = UUID.randomUUID().toString();
    public String title = "New conversation";
    public long createdAt = System.currentTimeMillis();
    public boolean testTask;
    public boolean hasModelOutput;
    public String sourcePath = "";
    public String sourcePackage = "";
    public String codeSnapshot = "";
    public String selectedCode = "";
    public String draft = "";
    public String draftCode;
    public List<Turn> turns = new ArrayList<>();

    /** Also recognises transcripts saved before the explicit history-membership flag existed. */
    public boolean belongsToHistory() {
        return hasModelOutput || turns.stream().anyMatch(t -> !t.modelOutput.isBlank()
                || !t.text.isBlank() || !t.testCode.isBlank());
    }

    public static final class Turn {
        public String question = "";
        public String code;
        public String status = "pending";
        public String text = "";
        public String modelOutput = "";
        public String error = "";
        public boolean grounded;
        public List<KnowledgeChunk> sources = new ArrayList<>();
        public String testCode = "";
        public String targetPath = "";
        public String filePath = "";
    }

    public static final class History {
        public String activeId = "";
        public List<Conversation> conversations = new ArrayList<>();

        public Conversation active() {
            return conversations.stream().filter(c -> c.id.equals(activeId)).findFirst().orElse(null);
        }

        public Conversation add(Conversation conversation) {
            conversations.add(0, conversation);
            activeId = conversation.id;
            return conversation;
        }

        public Conversation newChat() {
            Conversation current = active();
            if (current != null && !current.testTask && !current.belongsToHistory()) return current;
            Conversation draft = conversations.stream()
                    .filter(c -> !c.testTask && !c.belongsToHistory() && !c.draft.isBlank())
                    .findFirst().orElseGet(() -> conversations.stream()
                            .filter(c -> !c.testTask && !c.belongsToHistory()).findFirst().orElse(null));
            if (draft == null) return add(new Conversation());
            activeId = draft.id;
            return draft;
        }

        /** A restarted IDE cannot resume an HTTP request; never silently resend it. */
        public void recoverInterruptedRequests() {
            for (Conversation conversation : conversations) {
                conversation.hasModelOutput = conversation.belongsToHistory();
                for (Turn turn : conversation.turns) {
                    if ("pending".equals(turn.status)) {
                        turn.status = "failed";
                        turn.error = "The request was interrupted when the IDE closed. Retry to send it again.";
                        if (conversation.draft.isBlank()) {
                            conversation.draft = turn.question;
                            conversation.draftCode = turn.code;
                        }
                    }
                }
            }
        }
    }
}
