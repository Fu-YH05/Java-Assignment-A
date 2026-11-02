package com.cst3492.ta.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownContentTest {
    @Test void existingCodeBlockFollowsSystemBackgroundAndUpdatesHighlightingOnThemeChanges() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            Object previousBackground = javax.swing.UIManager.get("Panel.background");
            Object previousForeground = javax.swing.UIManager.get("Label.foreground");
            try {
                var block = new CodeBlock("public int count = 42;\n", "java",
                        new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
                var layout = (java.awt.BorderLayout) block.getLayout();
                var header = (javax.swing.JPanel) layout.getLayoutComponent(java.awt.BorderLayout.NORTH);
                var scroll = (javax.swing.JScrollPane) layout.getLayoutComponent(java.awt.BorderLayout.CENTER);
                var area = (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) scroll.getViewport().getView();
                java.awt.Color previousKeyword = null;
                for (int rgb : new int[]{0xf7f8fa, 0x2b2d30, 0xffffff}) {
                    var background = new javax.swing.plaf.ColorUIResource(rgb);
                    var foreground = new javax.swing.plaf.ColorUIResource(rgb == 0x2b2d30 ? 0xdfe1e5 : 0x202124);
                    javax.swing.UIManager.put("Panel.background", background);
                    javax.swing.UIManager.put("Label.foreground", foreground);
                    javax.swing.SwingUtilities.updateComponentTreeUI(block);
                    assertEquals(background, block.getBackground());
                    assertEquals(background, header.getBackground());
                    assertEquals(background, area.getBackground());
                    assertEquals(background, scroll.getViewport().getBackground());
                    assertEquals(foreground, area.getForeground());
                    var keyword = area.getForegroundForToken(area.getTokenListForLine(0));
                    assertNotEquals(foreground, keyword);
                    if (previousKeyword != null) assertNotEquals(previousKeyword, keyword);
                    previousKeyword = keyword;
                    assertEquals("public int count = 42;\n", area.getText());
                }
            } finally {
                javax.swing.UIManager.put("Panel.background", previousBackground);
                javax.swing.UIManager.put("Label.foreground", previousForeground);
            }
        });
    }

    @Test void codeHighlightingRespectsStringsCommentsWhitespaceAndUnknownLanguages() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            String code = "public class Demo {\n\tString s = \"https://example.com\"; // 注释\n"
                    + "/* multi\nline */ int count = 42;\n}\n\n";
            var font = new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14);
            var block = new CodeBlock(code, "JAVA", font);
            var scroll = (javax.swing.JScrollPane) ((java.awt.BorderLayout) block.getLayout())
                    .getLayoutComponent(java.awt.BorderLayout.CENTER);
            var area = (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) scroll.getViewport().getView();
            javax.swing.SwingUtilities.updateComponentTreeUI(block);
            assertEquals(code, area.getText());
            assertFalse(area.isEditable());
            assertFalse(area.getLineWrap());
            var string = area.getTokenListForLine(1);
            while (string != null && string.getType() != org.fife.ui.rsyntaxtextarea.TokenTypes.LITERAL_STRING_DOUBLE_QUOTE)
                string = string.getNextToken();
            assertNotNull(string);
            assertEquals("\"https://example.com\"", string.getLexeme());
            assertNotEquals(area.getForeground(), area.getForegroundForToken(string));
            assertEquals(org.fife.ui.rsyntaxtextarea.TokenTypes.COMMENT_MULTILINE,
                    area.getTokenListForLine(3).getType());
            var plain = new CodeBlock(code, "unknown-language", font);
            var plainScroll = (javax.swing.JScrollPane) ((java.awt.BorderLayout) plain.getLayout())
                    .getLayoutComponent(java.awt.BorderLayout.CENTER);
            var plainArea = (org.fife.ui.rsyntaxtextarea.RSyntaxTextArea) plainScroll.getViewport().getView();
            assertEquals("text/plain", plainArea.getSyntaxEditingStyle());
            assertEquals(code, plainArea.getText());
        });
    }

    @Test void longCodeStartsAtTheLeftAndOnlyItsBodyScrollsInANarrowPanel() throws Exception {
        var window = new java.util.concurrent.atomic.AtomicReference<javax.swing.JFrame>();
        var block = new java.util.concurrent.atomic.AtomicReference<CodeBlock>();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                var frame = new javax.swing.JFrame("Code block scroll regression");
                window.set(frame);
                var content = new MarkdownContent("```java\npublic void addScore(String studentId) { // "
                        + "long comment ".repeat(30) + "\n    return;\n}\n```", ignored -> { },
                        new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
                block.set((CodeBlock) content.getComponent(0));
                frame.setContentPane(content);
                frame.setSize(320, 260);
                frame.setVisible(true);
            });
            // Flush deferred caret and layout work before inspecting the initial scroll position.
            javax.swing.SwingUtilities.invokeAndWait(() -> { });
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                var layout = (java.awt.BorderLayout) block.get().getLayout();
                var scroll = (javax.swing.JScrollPane) layout.getLayoutComponent(java.awt.BorderLayout.CENTER);
                var header = layout.getLayoutComponent(java.awt.BorderLayout.NORTH);
                assertEquals(0, scroll.getViewport().getViewPosition().x);
                assertEquals(0, scroll.getViewport().getViewPosition().y);
                assertTrue(scroll.getHorizontalScrollBar().isVisible());
                assertTrue(block.get().getWidth() <= window.get().getContentPane().getWidth());
                scroll.getHorizontalScrollBar().setValue(180);
                assertTrue(scroll.getViewport().getViewPosition().x > 0);
                assertEquals(0, header.getX() - block.get().getInsets().left);
                var area = (javax.swing.JTextArea) scroll.getViewport().getView();
                area.select(0, 6);
                assertEquals("public", area.getSelectedText());
            });
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(() -> { if (window.get() != null) window.get().dispose(); });
        }
    }

    @Test void formatsChineseListsCodeAndReferences() {
        String html = MarkdownContent.html("## 课程解释\n\n**重点**与`Bean`\n\n- 项目一\n- 项目二\n\n"
                + "```java\nif (x < 2) return;\n```\n\n[1](#source-1)");
        assertTrue(html.contains("<h2>课程解释</h2>"));
        assertTrue(html.contains("<strong>重点</strong>"));
        assertTrue(html.contains("<li>项目一</li>"));
        assertTrue(html.contains("<code class=\"language-java\">"));
        assertTrue(html.contains("x &lt; 2"));
        assertTrue(html.contains("href=\"#source-1\""));
        assertFalse(html.contains("**重点**"));
    }

    @Test void modelOutputCannotLoadRemoteImagesOrInjectHtml() {
        String html = MarkdownContent.html("<img src='https://example.com/tracker'>\n\n"
                + "![Course diagram](https://example.com/tracker.png)\n\n[open](javascript:alert%281%29)");
        assertFalse(html.contains("<img"));
        assertTrue(html.contains("Course diagram"));
        assertFalse(html.contains("href=\"javascript:"));
        assertTrue(html.contains("&lt;img"));
    }

    @Test void nestedImagesCannotSmuggleRemoteImageThroughAltText() {
        String html = MarkdownContent.html(
                "![![inner](https://example.invalid/inner.png)](https://example.invalid/outer.png)\n\n"
                        + "![![![deep](https://example.invalid/deep.png)](https://example.invalid/mid.png)]"
                        + "(https://example.invalid/outer.png)");
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("example.invalid"));
        assertTrue(html.contains("inner"));
        assertTrue(html.contains("deep"));
    }

    @Test void chinesePunctuationInBoldDoesNotLeakMarkersOrAlterCode() {
        String html = MarkdownContent.html("**依赖注入（DI）**让对象解耦。\n\n`**依赖注入（DI）**让`\n\n"
                + "```java\n// **依赖注入（DI）**让\n```");
        assertTrue(html.contains("<strong>依赖注入（DI）</strong>让对象"));
        assertTrue(html.contains("<code>**依赖注入（DI）**让</code>"));
        assertTrue(html.contains("// **依赖注入（DI）**让"));
        assertFalse(MarkdownContent.html("\\*\\*依赖注入（DI）\\*\\*让").contains("<strong>"));
    }

    @Test void existingProseFollowsLookAndFeelChanges() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var pane = MarkdownContent.plain("中文正文");
            Object previous = javax.swing.UIManager.get("Label.foreground");
            try {
                javax.swing.UIManager.put("Label.foreground", new javax.swing.plaf.ColorUIResource(0xabcde0));
                pane.updateUI();
                assertTrue(pane.getText().contains("#abcde0"));
                assertTrue(assertDoesNotThrow(() -> pane.getDocument().getText(0,
                        pane.getDocument().getLength())).contains("中文正文"));
            } finally {
                javax.swing.UIManager.put("Label.foreground", previous);
            }
        });
    }

    @Test void copyCodeWritesOnlyTheOriginalCodeToClipboard() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            String code = "if (value < 2) {\n    return \"中文\";\n}\n";
            var content = new MarkdownContent("```java\n" + code + "```", ignored -> { },
                    new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
            var codePanel = (javax.swing.JPanel) content.getComponent(0);
            var header = (javax.swing.JPanel) ((java.awt.BorderLayout) codePanel.getLayout())
                    .getLayoutComponent(java.awt.BorderLayout.NORTH);
            var copy = (javax.swing.JButton) ((java.awt.BorderLayout) header.getLayout())
                    .getLayoutComponent(java.awt.BorderLayout.EAST);
            copy.doClick();
            assertEquals("Copied", copy.getText());
            Object clipboard = assertDoesNotThrow(() -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(java.awt.datatransfer.DataFlavor.stringFlavor));
            assertTrue(code.equals(clipboard), "Code clipboard contents must match the source, without Markdown wrappers");
        });
    }
}
