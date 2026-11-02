package com.cst3492.ta.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownContentTest {
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
