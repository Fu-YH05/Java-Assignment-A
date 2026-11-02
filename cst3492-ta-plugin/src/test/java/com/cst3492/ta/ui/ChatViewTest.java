package com.cst3492.ta.ui;

import org.junit.jupiter.api.Test;
import com.cst3492.ta.model.KnowledgeChunk;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ChatViewTest {
    @Test void sendsMultilineInputOnceAndKeepsNewDraftOnFailure() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> requests = new ArrayList<>();
            ChatView view = new ChatView((q, c) -> requests.add(q), () -> { }, () -> { }, ignored -> { });
            JTextArea input = input(view);
            assertFalse(button(view, "Send").isEnabled());
            input.setText("测试空字符串\n并检查边界情况");
            button(view, "Send").doClick();
            input.getActionMap().get("send-message").actionPerformed(null);
            assertEquals(List.of("测试空字符串\n并检查边界情况"), requests);
            assertTrue(input.getText().isEmpty());
            input.setText("下一条问题草稿");
            view.fail("embedding endpoint returned HTTP 502");
            assertEquals("下一条问题草稿", input.getText());
            button(view, "Retry").doClick();
            assertEquals(2, requests.size());
            assertEquals("下一条问题草稿", input.getText());
            view.answer("**已恢复**", List.of(), false);
            assertTrue(button(view, "Send").isEnabled());
        });
    }

    @Test void selectedCodeAndQuestionSurviveFailureAndRetry() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> contexts = new ArrayList<>();
            ChatView view = new ChatView((q, c) -> contexts.add(c), () -> { }, () -> { }, ignored -> { });
            String code = "class Bean {}";
            view.askQuestion("解释这段代码", code);
            view.fail("No API key configured");
            assertEquals("解释这段代码", input(view).getText());
            assertTrue(descendants(view).stream().filter(c -> c instanceof JLabel)
                    .map(c -> ((JLabel)c).getText()).anyMatch(t -> t.contains("Selected Java code")));
            button(view, "Retry").doClick();
            assertEquals(List.of(code, code), contexts);
            assertEquals("", input(view).getText());
        });
    }

    @Test void shiftEnterInsertsNewlineWithoutSubmitting() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> requests = new ArrayList<>();
            ChatView view = new ChatView((q, c) -> requests.add(q), () -> { }, () -> { }, ignored -> { });
            JTextArea input = input(view);
            input.setText("one");
            input.setCaretPosition(3);
            Object binding = input.getInputMap().get(KeyStroke.getKeyStroke("shift ENTER"));
            input.getActionMap().get(binding).actionPerformed(new java.awt.event.ActionEvent(input, 0, ""));
            assertEquals("one\n", input.getText());
            assertTrue(requests.isEmpty());
        });
    }

    @Test void sourceDisclosureDeduplicatesAndOpensTheCorrectEvidence() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChatView view = new ChatView((q, c) -> { }, () -> { }, () -> { }, ignored -> { });
            KnowledgeChunk source = new KnowledgeChunk();
            source.docId = "第1章 (1)";
            source.docName = source.docId + ".pdf";
            source.pageFrom = source.pageTo = 6;
            view.askQuestion("问题", null);
            view.answer("课程说明 [" + source.citationLabel() + "]", List.of(source, source), true);
            JToggleButton toggle = descendants(view).stream().filter(c -> c instanceof JToggleButton)
                    .map(c -> (JToggleButton)c).findFirst().orElseThrow();
            assertEquals("Course sources · 1", toggle.getText());
            assertNotNull(toggle.getIcon());
            assertNotNull(toggle.getSelectedIcon());
            assertFalse(toggle.isSelected());
            var sourceRow = descendants(view).stream().filter(c -> source.citationLabel().equals(
                    c instanceof JComponent component ? component.getToolTipText() : null))
                    .findFirst().orElseThrow();
            assertFalse(sourceRow.getParent().isVisible());
            toggle.doClick();
            assertTrue(sourceRow.getParent().isVisible());
            toggle.doClick();
            assertFalse(sourceRow.getParent().isVisible());
        });
    }

    @Test void arrivingAnswerDoesNotMoveTheReadersScrollPosition() throws Exception {
        AtomicReference<JFrame> frame = new AtomicReference<>();
        AtomicReference<ChatView> chat = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ChatView view = new ChatView((q, c) -> { }, () -> { }, () -> { }, ignored -> { });
                JFrame window = new JFrame("Chat scroll regression");
                frame.set(window);
                chat.set(view);
                window.setContentPane(view);
                window.setSize(400, 650);
                window.setVisible(true);
                view.askQuestion("第一条问题", null);
                view.answer("## 回答\n\n" + "阅读中的课程解释。\n\n".repeat(60), List.of(), false);
            });
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> {
                JScrollPane history = descendants(chat.get()).stream().filter(c -> c instanceof JScrollPane s
                        && s.getViewport().getView() instanceof JPanel).map(c -> (JScrollPane)c).findFirst().orElseThrow();
                assertTrue(history.getVerticalScrollBar().getMaximum() > 1000);
                history.getVerticalScrollBar().setValue(80);
                chat.get().askQuestion("第二条问题", null);
            });
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> chat.get().answer("新回答\n\n".repeat(40), List.of(), false));
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> {
                JScrollPane history = descendants(chat.get()).stream().filter(c -> c instanceof JScrollPane s
                        && s.getViewport().getView() instanceof JPanel).map(c -> (JScrollPane)c).findFirst().orElseThrow();
                assertEquals(80, history.getVerticalScrollBar().getValue());
                assertTrue(button(chat.get(), "↓ New response").isVisible());
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> { if (frame.get() != null) frame.get().dispose(); });
        }
    }

    @Test void optionsFollowThemeAndClearPreservesDraftAndIsDisabledDuringRequests() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Chat clear regression");
            LookAndFeel originalLookAndFeel = UIManager.getLookAndFeel();
            try {
                ChatView view = new ChatView((q, c) -> { }, () -> { }, () -> { }, ignored -> { });
                try { UIManager.setLookAndFeel(new javax.swing.plaf.nimbus.NimbusLookAndFeel()); }
                catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
                frame.setContentPane(view);
                frame.setSize(400, 650);
                frame.setVisible(true);
                view.askQuestion("Previous question", null);
                JButton options = button(view, "···");
                options.doClick();
                JPopupMenu popup = (JPopupMenu) MenuSelectionManager.defaultManager().getSelectedPath()[0];
                assertEquals(new JPopupMenu().getUI().getClass(), popup.getUI().getClass());
                JMenuItem clear = java.util.Arrays.stream(popup.getComponents())
                        .filter(c -> c instanceof JMenuItem item && item.getText().equals("Clear conversation"))
                        .map(c -> (JMenuItem)c).findFirst().orElseThrow();
                assertFalse(clear.isEnabled());
                MenuSelectionManager.defaultManager().clearSelectedPath();
                view.answer("Previous answer", List.of(), false);
                input(view).setText("Unsent draft");
                options.doClick();
                assertTrue(clear.isEnabled());
                clear.doClick();
                assertEquals("Unsent draft", input(view).getText());
                assertTrue(descendants(view).stream().anyMatch(c -> c instanceof JLabel label
                        && label.getText().equals("Ask your course TA")));
                assertFalse(descendants(view).stream().filter(c -> c instanceof JEditorPane)
                        .map(c -> ((JEditorPane)c).getText()).anyMatch(t -> t.contains("Previous answer")));
            } finally {
                MenuSelectionManager.defaultManager().clearSelectedPath();
                frame.dispose();
                try { UIManager.setLookAndFeel(originalLookAndFeel); }
                catch (UnsupportedLookAndFeelException e) { throw new AssertionError(e); }
            }
        });
    }

    private static JTextArea input(ChatView view) {
        return descendants(view).stream().filter(c -> c instanceof JTextArea
                && "Message to CST3492 TA".equals(c.getAccessibleContext().getAccessibleName()))
                .map(c -> (JTextArea)c).findFirst().orElseThrow();
    }

    private static JButton button(ChatView view, String text) {
        return descendants(view).stream().filter(c -> c instanceof JButton b && b.getText().equals(text))
                .map(c -> (JButton)c).findFirst().orElseThrow();
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }
}
