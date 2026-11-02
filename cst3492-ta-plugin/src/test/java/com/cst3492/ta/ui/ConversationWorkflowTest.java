package com.cst3492.ta.ui;

import com.cst3492.ta.model.Conversation;
import com.cst3492.ta.service.GeneratedTestCode;
import com.cst3492.ta.util.JsonUtil;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationWorkflowTest {
    @Test void newChatReusesUnsentDraftAcrossHistoryVisitsAndRestart() throws Exception {
        Conversation.History history = new Conversation.History();
        Conversation draft = history.newChat();
        draft.draft = "尚未发送的草稿";
        for (int i = 0; i < 8; i++) assertSame(draft, history.newChat());
        Conversation established = history.add(new Conversation());
        established.hasModelOutput = true;
        assertSame(draft, history.newChat());
        assertEquals(2, history.conversations.size());
        Conversation.History restored = JsonUtil.mapper().readValue(
                JsonUtil.mapper().writeValueAsBytes(history), Conversation.History.class);
        restored.recoverInterruptedRequests();
        assertEquals(draft.id, restored.newChat().id);
        assertEquals(draft.draft, restored.newChat().draft);
        assertEquals(1, restored.conversations.stream().filter(Conversation::belongsToHistory).count());
    }

    @Test void onlyActualOutputPromotesHistoryIncludingOutputThatFailsValidation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Conversation c = new Conversation();
            c.testTask = true;
            ChatView view = new ChatView((q, code) -> {}, () -> {}, () -> {}, url -> {}, c, () -> {}, (turn, done) -> done.run());
            input(view).setText("测试场景");
            button(view, "Generate tests").doClick();
            assertFalse(c.belongsToHistory());
            view.fail("HTTP 503");
            assertFalse(c.belongsToHistory());
            button(view, "Retry").doClick();
            view.receivedModelOutput("```java\nclass Incomplete {\n```");
            view.fail("Invalid Java syntax");
            assertTrue(c.belongsToHistory());
            assertFalse(c.turns.getFirst().modelOutput.isBlank());
            input(view).setText("再次生成");
            button(view, "Generate tests").doClick();
            view.fail("HTTP 503");
            assertTrue(c.belongsToHistory(), "Later failures cannot remove an established conversation");
        });
    }

    @Test void legacyEmptyConversationsStayOutOfHistoryAndPagesNeverOverlap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Conversation.History history = new Conversation.History();
            for (int i = 0; i < 8; i++) history.add(new Conversation());
            Conversation established = history.add(new Conversation());
            Conversation.Turn oldAnswer = new Conversation.Turn();
            oldAnswer.status = "complete";
            oldAnswer.text = "已有模型回答";
            established.turns.add(oldAnswer);
            history.recoverInterruptedRequests();
            ConversationDeck deck = new ConversationDeck(history, c -> new ChatView((q, code) -> {}, () -> {},
                    () -> {}, url -> {}, c, () -> {}, (turn, done) -> done.run()), () -> {});
            JPanel pages = components(deck).stream().filter(c -> "Conversation pages".equals(c.getName()))
                    .map(c -> (JPanel)c).findFirst().orElseThrow();
            JList<?> list = components(deck).stream().filter(c -> c instanceof JList<?>).map(c -> (JList<?>)c).findFirst().orElseThrow();
            for (int i = 0; i < 5; i++) {
                button(deck, "History").doClick();
                assertEquals(1, list.getModel().getSize());
                assertEquals(1, java.util.Arrays.stream(pages.getComponents()).filter(Component::isVisible).count());
                assertFalse(deck.activeView().isVisible());
                button(deck, "＋").doClick();
                assertEquals(1, java.util.Arrays.stream(pages.getComponents()).filter(Component::isVisible).count());
                assertTrue(deck.activeView().isVisible());
                deck.show(established);
            }
            assertEquals(9, history.conversations.size(), "Repeated clicks must not create extra empty records");
        });
    }

    @Test void boundSourceIsInlineReadOnlyAndRemainsAfterSendingAndRestoring() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Conversation c = new Conversation();
            c.testTask = true;
            c.sourcePath = "D:/fixture/Grade.java";
            c.codeSnapshot = "package demo;\nclass Grade { int score = 100; }";
            c.draft = "验证边界";
            ChatView view = new ChatView((q, code) -> {}, () -> {}, () -> {}, url -> {}, c, () -> {}, (turn, done) -> done.run());
            var source = components(view).stream().filter(component -> component instanceof org.fife.ui.rsyntaxtextarea.RSyntaxTextArea)
                    .map(component -> (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea)component).findFirst().orElseThrow();
            assertEquals(c.codeSnapshot, source.getText());
            assertFalse(source.isEditable());
            assertEquals("text/java", source.getSyntaxEditingStyle());
            button(view, "Generate tests").doClick();
            view.answerTest("package demo;\nclass GradeTest {}", "demo/GradeTest.java");
            assertEquals(2, components(view).stream().filter(component -> component instanceof CodeBlock).count());
            ChatView restored = new ChatView((q, code) -> fail("Unexpected model call"), () -> {}, () -> {}, url -> {}, c, () -> {}, (turn, done) -> done.run());
            assertEquals(2, components(restored).stream().filter(component -> component instanceof CodeBlock).count());
            assertTrue(components(restored).stream().noneMatch(component -> component instanceof JButton b && b.getText().contains("Snapshot")));
        });
    }

    @Test void backgroundResultReturnsToItsConversationAndRestoresWithoutCallingModel() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Conversation.History history = new Conversation.History();
            Conversation chat = history.add(new Conversation());
            chat.draft = "未发送的课程问题";
            Conversation test = new Conversation();
            test.testTask = true;
            test.sourcePath = "D:/fixture/src/main/java/Course.java";
            test.codeSnapshot = "class Course {}";
            test.draft = "验证空输入";
            List<String> sent = new ArrayList<>();
            Map<String, ChatView> views = new HashMap<>();
            ConversationDeck deck = new ConversationDeck(history, c -> {
                ChatView view = new ChatView((q, code) -> sent.add(c.id + ":" + q), () -> {}, () -> {}, url -> {},
                        c, () -> {}, (turn, done) -> { turn.filePath = "D:/fixture/src/test/java/CourseTest.java"; done.run(); });
                views.put(c.id, view);
                return view;
            }, () -> {});
            deck.addTest(test);
            ChatView target = deck.activeView();
            button(target, "Generate tests").doClick();
            input(target).setText("下次测试的草稿");
            deck.show(chat);
            target.answerTest("class CourseTest {}", "D:/fixture/src/test/java/CourseTest.java");
            assertSame(views.get(chat.id), deck.activeView());
            assertEquals("未发送的课程问题", input(deck.activeView()).getText());
            assertTrue(chat.turns.isEmpty());
            assertEquals("complete", test.turns.getFirst().status);
            assertEquals("下次测试的草稿", test.draft);
            button(target, "Create test file").doClick();
            assertFalse(test.turns.getFirst().filePath.isBlank());
            ChatView restored = new ChatView((q, code) -> fail("Restoring must never submit a request"), () -> {},
                    () -> {}, url -> {}, test, () -> {}, (turn, done) -> done.run());
            assertNotNull(button(restored, "Open test file"));
            assertEquals("下次测试的草稿", input(restored).getText());
            assertEquals(1, sent.size());
            assertEquals(1, test.turns.size());
        });
    }

    @Test void diskSnapshotRoundTripRetainsArtifactsAndRecoversOnlyInterruptedRequests() throws Exception {
        Conversation.History history = new Conversation.History();
        Conversation task = history.add(new Conversation());
        task.testTask = true;
        task.sourcePath = "D:/含空格 project/Grade.java";
        task.codeSnapshot = "class Grade { String s = \"<>&\"; }";
        task.draft = "用户的新草稿";
        Conversation.Turn complete = new Conversation.Turn();
        complete.status = "complete";
        complete.testCode = "class GradeTest {}\n";
        complete.filePath = "D:/含空格 project/test/GradeTest.java";
        task.turns.add(complete);
        Conversation.Turn pending = new Conversation.Turn();
        pending.question = "请求中的场景";
        pending.code = task.codeSnapshot;
        task.turns.add(pending);
        byte[] disk = JsonUtil.mapper().writeValueAsBytes(history);
        Conversation.History recovered = JsonUtil.mapper().readValue(disk, Conversation.History.class);
        recovered.recoverInterruptedRequests();
        assertEquals(history.activeId, recovered.activeId);
        assertEquals(task.codeSnapshot, recovered.active().codeSnapshot);
        assertEquals(task.draft, recovered.active().draft);
        assertEquals(complete.filePath, recovered.active().turns.getFirst().filePath);
        assertEquals(complete.testCode, recovered.active().turns.getFirst().testCode);
        assertEquals("failed", recovered.active().turns.get(1).status);
        assertEquals("pending", pending.status, "Snapshot must not alias live state");
        recovered.active().draft = "";
        recovered.active().turns.get(1).status = "pending";
        recovered.recoverInterruptedRequests();
        assertEquals(pending.question, recovered.active().draft);
        assertEquals(pending.code, recovered.active().draftCode);
    }

    @Test void retryUpdatesTheFailedTurnAndLeavesLaterDraftAndResultsIntact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Conversation c = new Conversation();
            c.testTask = true;
            List<String> requests = new ArrayList<>();
            ChatView view = new ChatView((q, code) -> requests.add(q), () -> {}, () -> {}, url -> {}, c, () -> {}, (turn, done) -> done.run());
            input(view).setText("第一个场景");
            button(view, "Generate tests").doClick();
            view.fail("HTTP 503");
            input(view).setText("第二个场景");
            button(view, "Generate tests").doClick();
            view.answerTest("class SecondTest {}", "SecondTest.java");
            input(view).setText("不要覆盖草稿");
            button(view, "Retry").doClick();
            view.answerTest("class FirstTest {}", "FirstTest.java");
            assertEquals(List.of("第一个场景", "第二个场景", "第一个场景"), requests);
            assertEquals(2, c.turns.size());
            assertEquals("class FirstTest {}", c.turns.getFirst().testCode);
            assertEquals("class SecondTest {}", c.turns.get(1).testCode);
            assertEquals("不要覆盖草稿", c.draft);
        });
    }

    @Test void extractsOneArtifactAndRefusesAmbiguousModelResponses() {
        assertEquals("package demo;\nclass DemoTest {}\n", GeneratedTestCode.extract("Here is the test:\n```java\npackage demo;\nclass DemoTest {}\n```\n"));
        assertEquals("class T {}\n", GeneratedTestCode.extract("class T {}"));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTestCode.extract("```java\nclass A {}\n```\n```java\nclass B {}\n```"));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTestCode.extract("```python\npass\n```"));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTestCode.extract(""));
    }

    private static JTextArea input(Container parent) {
        return components(parent).stream().filter(c -> c instanceof JTextArea
                && "Message to CST3492 TA".equals(c.getAccessibleContext().getAccessibleName()))
                .map(c -> (JTextArea)c).findFirst().orElseThrow();
    }
    private static JButton button(Container parent, String text) {
        return components(parent).stream().filter(c -> c instanceof JButton b && b.getText().equals(text))
                .map(c -> (JButton)c).findFirst().orElseThrow();
    }
    private static List<Component> components(Container parent) {
        List<Component> all = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            all.add(child);
            if (child instanceof Container container) all.addAll(components(container));
        }
        return all;
    }
}
