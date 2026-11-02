package com.cst3492.ta.ui;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.TokenTypes;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.util.Locale;

/** Read-only code with a VS Code-inspired palette; the header stays outside the viewport. */
final class CodeBlock extends JPanel {
    private final JPanel header;
    private final JLabel label;
    private final RSyntaxTextArea area;
    private final JScrollPane scroll;

    CodeBlock(String code, String language, Font font) {
        super(new BorderLayout());
        setAlignmentX(LEFT_ALIGNMENT);
        setMinimumSize(new Dimension(0, 0));

        header = new JPanel(new BorderLayout(8, 0));
        label = new JLabel(language);
        header.add(label, BorderLayout.WEST);
        JButton copy = MarkdownContent.smallButton("Copy code");
        copy.addActionListener(event -> MarkdownContent.copy(copy, code));
        header.add(copy, BorderLayout.EAST);

        area = new RSyntaxTextArea();
        // Disable caret-following before inserting text, so deferred caret updates cannot hide line starts.
        ((DefaultCaret) area.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        area.setEditable(false);
        area.setSyntaxEditingStyle(syntax(language));
        area.setFont(font);
        area.setUseSelectedTextColor(true);
        area.setHighlightCurrentLine(false);
        area.setBracketMatchingEnabled(false);
        area.setHyperlinksEnabled(false);
        area.setPopupMenu(null);
        area.setAntiAliasingEnabled(true);
        area.setTabSize(4);
        area.setLineWrap(false);
        area.setMargin(new Insets(12, 14, 12, 14));
        area.setText(code);
        area.setCaretPosition(0);
        // Keep source whitespace intact while avoiding an extra visible row for the closing fence.
        area.setRows(Math.min(18, Math.max(2, code.stripTrailing().split("\n", -1).length)));
        area.setColumns(1);
        area.getAccessibleContext().setAccessibleName(language + " code");
        scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setMinimumSize(new Dimension(0, 0));
        scroll.getVerticalScrollBar().setUnitIncrement(area.getLineHeight());
        scroll.getHorizontalScrollBar().setUnitIncrement(area.getFontMetrics(font).charWidth('m') * 4);
        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        applyTheme();
    }

    @Override public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override public void updateUI() {
        super.updateUI();
        if (area != null) applyTheme();
    }

    private void applyTheme() {
        // Plain Colors survive child UI updates when the IDE switches its look and feel.
        Color background = new Color(UIManager.getColor("Panel.background").getRGB());
        Color foreground = new Color(UIManager.getColor("Label.foreground").getRGB());
        Color border = UIManager.getColor("Separator.foreground");
        if (border == null) border = background.darker();
        boolean dark = background.getRed() * .2126 + background.getGreen() * .7152
                + background.getBlue() * .0722 < 128;
        setBackground(background);
        setBorder(BorderFactory.createLineBorder(border));
        header.setBackground(background);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, border),
                BorderFactory.createEmptyBorder(6, 12, 6, 10)));
        label.setForeground(foreground);
        area.setBackground(background);
        area.setForeground(foreground);
        area.setCaretColor(foreground);
        area.setSyntaxScheme(scheme(area.getFont(), foreground, dark));
        area.setSelectionColor(UIManager.getColor("TextArea.selectionBackground"));
        area.setSelectedTextColor(UIManager.getColor("TextArea.selectionForeground"));
        scroll.setBackground(background);
        scroll.getViewport().setBackground(background);
    }

    private static String syntax(String language) {
        return switch (language.toLowerCase(Locale.ROOT)) {
            case "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA;
            case "js", "javascript" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
            case "ts", "typescript" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT;
            case "json" -> SyntaxConstants.SYNTAX_STYLE_JSON;
            case "xml" -> SyntaxConstants.SYNTAX_STYLE_XML;
            case "html" -> SyntaxConstants.SYNTAX_STYLE_HTML;
            case "css" -> SyntaxConstants.SYNTAX_STYLE_CSS;
            case "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL;
            case "py", "python" -> SyntaxConstants.SYNTAX_STYLE_PYTHON;
            case "sh", "bash", "shell" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL;
            case "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML;
            case "kt", "kotlin" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN;
            default -> SyntaxConstants.SYNTAX_STYLE_NONE;
        };
    }

    private static SyntaxScheme scheme(Font font, Color foreground, boolean dark) {
        SyntaxScheme scheme = new SyntaxScheme(font);
        for (var style : scheme.getStyles()) {
            if (style == null) continue;
            style.foreground = foreground;
            style.background = null;
            style.font = font;
            style.underline = false;
        }
        color(scheme, dark ? 0x569cd6 : 0x0000ff, TokenTypes.RESERVED_WORD, TokenTypes.DATA_TYPE, TokenTypes.LITERAL_BOOLEAN,
                TokenTypes.MARKUP_TAG_NAME, TokenTypes.MARKUP_TAG_DELIMITER);
        color(scheme, dark ? 0xc586c0 : 0xaf00db, TokenTypes.RESERVED_WORD_2, TokenTypes.PREPROCESSOR);
        color(scheme, dark ? 0xdcdcaa : 0x795e26, TokenTypes.FUNCTION, TokenTypes.ANNOTATION);
        color(scheme, dark ? 0xce9178 : 0xa31515, TokenTypes.LITERAL_STRING_DOUBLE_QUOTE, TokenTypes.LITERAL_CHAR,
                TokenTypes.LITERAL_BACKQUOTE, TokenTypes.MARKUP_TAG_ATTRIBUTE_VALUE);
        color(scheme, dark ? 0xb5cea8 : 0x087f50, TokenTypes.LITERAL_NUMBER_DECIMAL_INT, TokenTypes.LITERAL_NUMBER_FLOAT,
                TokenTypes.LITERAL_NUMBER_HEXADECIMAL);
        color(scheme, dark ? 0x7fa46e : 0x008000, TokenTypes.COMMENT_EOL, TokenTypes.COMMENT_MULTILINE,
                TokenTypes.COMMENT_DOCUMENTATION, TokenTypes.MARKUP_COMMENT);
        color(scheme, dark ? 0x9cdcfe : 0x001080, TokenTypes.VARIABLE, TokenTypes.MARKUP_TAG_ATTRIBUTE,
                TokenTypes.COMMENT_KEYWORD, TokenTypes.COMMENT_MARKUP);
        return scheme;
    }

    private static void color(SyntaxScheme scheme, int rgb, int... types) {
        for (int type : types) scheme.getStyle(type).foreground = new Color(rgb);
    }
}
