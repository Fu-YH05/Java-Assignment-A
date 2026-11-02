package com.cst3492.ta.ui;

import com.cst3492.ta.history.ChatHistory;
import com.cst3492.ta.model.KnowledgeChunk;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryViewTest {
    @Test void testConversationWaitsForScenarioAndRestoresGeneratedArtifact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger(); var state = new AtomicReference<ChatHistory>();
            var view = view(calls); view.setHistoryListener(state::set);
            view.startTestConversation("/project/src/Bean.java", "demo", "int size()", "package demo; class Bean {}");
            assertEquals(0, calls.get());
            input(view).setText("Test empty input");
            find(view, JButton.class, b -> b.getText().equals("Send")).doClick();
            assertEquals(1, calls.get());
            String code = "package demo; class BeanTest {}";
            view.answerTest(code);
            assertEquals(code, state.get().active().turns.get(0).testCode);
            assertEquals("demo", state.get().active().sourcePackage);
            var reopened = view(calls); reopened.restoreHistory(state.get());
            assertNotNull(find(reopened, JButton.class, b -> b.getText().equals("Create Test File")));
            assertEquals(1, calls.get());
        });
    }
    @Test void newChatAndReopeningRestoreAnswersSourcesAndPerSessionDraftsWithoutCallingProvider() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger();
            var state = new AtomicReference<ChatHistory>();
            var view = view(calls);
            view.setHistoryListener(state::set);
            var source = new KnowledgeChunk(); source.docId = "lec"; source.docName = "Lecture.pdf";
            source.pageFrom = source.pageTo = 3;
            view.askQuestion("First question", "class Bean {}");
            view.answer("First answer [Lecture.pdf, p.3]", List.of(source), true);
            String firstId = state.get().activeSessionId;
            input(view).setText("First draft");
            view.newConversation();
            input(view).setText("Second draft");
            view.openConversation(firstId);
            assertEquals("First draft", input(view).getText());
            assertEquals(1, calls.get());
            assertEquals(1, turns(view).getModel().getSize());
            assertTrue(turns(view).getModel().getElementAt(0).toString().startsWith("1. First question"));
            assertTrue(hasText(view, "First answer"));
            assertNotNull(find(view, JToggleButton.class, b -> b.getText().equals("Course sources · 1")));
            var reopened = view(calls);
            reopened.restoreHistory(state.get());
            assertTrue(hasText(reopened, "First answer"));
            assertEquals("First draft", input(reopened).getText());
            assertEquals(1, calls.get(), "Opening history must never re-send a request");
        });
    }

    @Test void retryUpdatesItsOriginalTurnAndSessionChangesAreBlockedWhileBusy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger(); var state = new AtomicReference<ChatHistory>();
            var view = view(calls); view.setHistoryListener(state::set);
            view.askQuestion("Question", "class Context {}");
            String sessionId = state.get().activeSessionId;
            view.newConversation(); view.deleteCurrentConversation();
            assertEquals(sessionId, state.get().activeSessionId);
            assertEquals(1, state.get().sessions.size());
            view.fail("HTTP 503");
            find(view, JButton.class, b -> b.getText().equals("Retry")).doClick();
            view.answer("Recovered", List.of(), false);
            assertEquals(2, calls.get());
            assertEquals(1, state.get().active().turns.size());
            assertEquals("answered", state.get().active().turns.get(0).status);
            assertEquals("class Context {}", state.get().active().turns.get(0).code);
            view.newConversation();
            view.askQuestion("Cancelled question", null); view.cancelled();
            assertEquals("cancelled", state.get().active().turns.get(0).status);
            assertEquals("Cancelled question", input(view).getText());
            view.deleteCurrentConversation();
            assertEquals(sessionId, state.get().activeSessionId);
            assertTrue(hasText(view, "Recovered"));
        });
    }

    @Test void interruptedHistoryDoesNotShowSpinnerOrResend() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var calls = new AtomicInteger(); var state = new ChatHistory();
            var turn = new ChatHistory.Turn(); turn.question = "Interrupted question";
            state.active().turns.add(turn);
            var view = view(calls); view.restoreHistory(state);
            assertEquals(0, calls.get());
            assertEquals("interrupted", turn.status);
            assertTrue(hasText(view, "IDE closed"));
            assertNull(find(view, JProgressBar.class, b -> true));
        });
    }

    @Test void turnSelectionScrollsToTheRequestedQuestion() throws Exception {
        var frame = new AtomicReference<JFrame>(); var chat = new AtomicReference<ChatView>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                var view = view(new AtomicInteger()); chat.set(view);
                JFrame window = new JFrame("Turn navigation"); frame.set(window);
                window.setContentPane(view); window.setSize(680, 650); window.setVisible(true);
                for (int i = 1; i <= 3; i++) {
                    view.askQuestion("Question " + i, null);
                    view.answer("Answer " + i + "\n\n" + "Course explanation.\n\n".repeat(30), List.of(), false);
                }
            });
            SwingUtilities.invokeAndWait(() -> turns(chat.get()).setSelectedIndex(1));
            SwingUtilities.invokeAndWait(() -> {});
            SwingUtilities.invokeAndWait(() -> {
                ChatView view = chat.get();
                JPanel question = find(view, JPanel.class, p -> p.getLayout() instanceof BorderLayout
                        && java.util.Arrays.stream(p.getComponents()).anyMatch(c -> c instanceof JEditorPane e
                        && e.getText().contains("Question 2")));
                JScrollPane scroll = find(view, JScrollPane.class, s -> s.getViewport().getView() instanceof JPanel);
                assertNotNull(question);
                assertEquals(question.getY(), scroll.getVerticalScrollBar().getValue());
            });
        } finally { SwingUtilities.invokeAndWait(() -> { if (frame.get() != null) frame.get().dispose(); }); }
    }

    private static ChatView view(AtomicInteger calls) {
        return new ChatView((q,c) -> calls.incrementAndGet(), () -> {}, () -> {}, url -> {});
    }
    private static JTextArea input(ChatView view) {
        return find(view, JTextArea.class, a -> "Message to CST3492 TA".equals(a.getAccessibleContext().getAccessibleName()));
    }
    private static JList<?> turns(ChatView view) {
        return find(view, JList.class, a -> "Conversation turns".equals(a.getAccessibleContext().getAccessibleName()));
    }
    private static boolean hasText(Container root, String text) {
        return find(root, JEditorPane.class, p -> p.getText().contains(text)) != null;
    }
    private static <T> T find(Container root, Class<T> type, java.util.function.Predicate<T> predicate) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && predicate.test(type.cast(child))) return type.cast(child);
            if (child instanceof Container container) {
                T match = find(container, type, predicate); if (match != null) return match;
            }
        }
        return null;
    }
}
