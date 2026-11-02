package com.cst3492.ta.ui;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** CommonMark paragraphs and independently scrollable, copyable code blocks. */
final class MarkdownContent extends JPanel {
    private static final Parser PARSER = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().escapeHtml(true)
            .sanitizeUrls(true).build();
    // CommonMark's punctuation flanking rule leaves this common Chinese LLM output unparsed.
    private static final Pattern CJK_STRONG = Pattern.compile("\\*\\*([^*\\r\\n]*[）】。，！？：])\\*\\*(?=\\p{IsHan})");

    MarkdownContent(String markdown, Consumer<String> linkHandler, Font codeFont) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        Node document = parse(markdown == null ? "" : markdown);
        // Remote images in model output must not trigger background network requests.
        StringBuilder prose = new StringBuilder();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof FencedCodeBlock code) {
                flush(prose, linkHandler);
                String language = code.getInfo().isBlank() ? "Code" : code.getInfo().split("\\s+", 2)[0];
                add(new CodeBlock(code.getLiteral(), language, codeFont));
            } else if (node instanceof IndentedCodeBlock code) {
                flush(prose, linkHandler);
                add(new CodeBlock(code.getLiteral(), "Code", codeFont));
            } else {
                prose.append(RENDERER.render(node));
            }
        }
        flush(prose, linkHandler);
    }

    static String html(String markdown) {
        Node document = parse(markdown);
        return RENDERER.render(document);
    }

    private static Node parse(String markdown) {
        Node document = PARSER.parse(markdown);
        removeImages(document);
        repairChineseStrong(document, markdown.split("\\r\\n|\\r|\\n", -1));
        return document;
    }

    private static void repairChineseStrong(Node parent, String[] lines) {
        for (Node node = parent.getFirstChild(); node != null; ) {
            Node next = node.getNext();
            if (node instanceof Text text) {
                boolean escaped = text.getSourceSpans().stream().anyMatch(span ->
                        lines[span.getLineIndex()].substring(span.getColumnIndex(),
                                span.getColumnIndex() + span.getLength()).contains("\\*"));
                if (escaped) { node = next; continue; }
                var matches = CJK_STRONG.matcher(text.getLiteral());
                int end = 0;
                while (matches.find()) {
                    if (matches.start() > end) node.insertBefore(new Text(text.getLiteral().substring(end, matches.start())));
                    StrongEmphasis strong = new StrongEmphasis();
                    strong.appendChild(new Text(matches.group(1)));
                    node.insertBefore(strong);
                    end = matches.end();
                }
                if (end > 0) {
                    if (end < text.getLiteral().length()) node.insertBefore(new Text(text.getLiteral().substring(end)));
                    node.unlink();
                }
            } else {
                // Code nodes store literal content, not text children, so copied code is untouched.
                repairChineseStrong(node, lines);
            }
            node = next;
        }
    }

    private static void removeImages(Node node) {
        for (Node child = node.getFirstChild(); child != null; ) {
            Node next = child.getNext();
            dropImage(child);
            child = next;
        }
    }

    /** Replaces an image with the text of its description, so no URL can be fetched. */
    private static void dropImage(Node child) {
        if (!(child instanceof Image image)) {
            removeImages(child);
            return;
        }
        while (image.getParent() != null && image.getFirstChild() != null) {
            Node alt = image.getFirstChild();
            alt.unlink();
            image.insertBefore(alt);
            // The promoted description can itself be, or contain, another image.
            dropImage(alt);
        }
        if (image.getParent() != null) image.unlink();
    }

    private void flush(StringBuilder prose, Consumer<String> links) {
        if (!prose.isEmpty()) {
            add(prose(prose.toString(), links));
            prose.setLength(0);
        }
    }

    static JEditorPane plain(String text) {
        return prose("<p>" + escape(text).replace("\n", "<br>") + "</p>", ignored -> { });
    }

    private static JEditorPane prose(String body, Consumer<String> links) {
        JEditorPane pane = new JEditorPane() {
            @Override public void updateUI() {
                super.updateUI();
                Object body = getClientProperty("cst3492.htmlBody");
                if (body instanceof String html) applyStyle(this, html);
            }
            @Override public Dimension getPreferredSize() {
                int width = getParent() == null ? 360 : getParent().getWidth();
                if (width > 0) setSize(width, Short.MAX_VALUE);
                Dimension size = super.getPreferredSize();
                return new Dimension(0, size.height);
            }
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        pane.setEditorKit(new HTMLEditorKit());
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        pane.putClientProperty("cst3492.htmlBody", body);
        applyStyle(pane, body);
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setBorder(null);
        ((DefaultCaret) pane.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        pane.setCaretPosition(0);
        pane.setAlignmentX(LEFT_ALIGNMENT);
        pane.setMinimumSize(new Dimension(0, 0));
        pane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) links.accept(event.getDescription());
        });
        return pane;
    }

    private static void applyStyle(JEditorPane pane, String body) {
        Font font = UIManager.getFont("Label.font");
        pane.setFont(font);
        Color foreground = UIManager.getColor("Label.foreground");
        Color link = UIManager.getColor("Link.activeForeground");
        if (link == null) link = UIManager.getColor("Component.focusColor");
        if (link == null) link = foreground;
        pane.setText("<html><head><style>body { margin:0; font-family:'" + font.getFamily()
                + "'; font-size:" + font.getSize() + "pt; color:" + hex(foreground)
                + "; } p {margin-top:4px; margin-bottom:10px;}"
                + " h1,h2,h3,h4,h5,h6 {margin-top:12px; margin-bottom:6px;}"
                + " h1 {font-size:145%;} h2 {font-size:130%;} h3 {font-size:115%;}"
                + " ul,ol {margin-top:3px; margin-bottom:10px; margin-left:20px;}"
                + " li {margin-bottom:5px;} code,pre {font-family:monospace;}"
                + " a {color:" + hex(link) + ";} blockquote {margin-left:12px;}"
                + "</style></head><body>" + body + "</body></html>");
    }

    static JButton smallButton(String text) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(3, 7, 3, 7));
        button.setFont(UIManager.getFont("Label.font").deriveFont(Font.PLAIN,
                Math.max(11f, UIManager.getFont("Label.font").getSize() - 1f)));
        return button;
    }

    static void copy(JButton button, String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            String label = button.getText();
            button.setText("Copied");
            button.setEnabled(false);
            Timer reset = new Timer(1400, event -> { button.setText(label); button.setEnabled(true); });
            reset.setRepeats(false);
            reset.start();
        } catch (IllegalStateException unavailable) {
            button.setToolTipText("Clipboard is busy. Please try again.");
        }
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
