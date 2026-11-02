package com.cst3492.ta.ui;

import com.cst3492.ta.model.KnowledgeChunk;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The actual chat UI; service calls stay in ChatPanel, so this view can be exercised offline. */
final class ChatView extends JPanel {
    private final BiConsumer<String, String> ask;
    private final Runnable buildIndex;
    private final Runnable settings;
    private final Consumer<String> openLink;
    private final MessageList messages = new MessageList();
    private final JScrollPane history = new JScrollPane(messages);
    private final JTextArea input = new JTextArea(3, 1) {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (getText().isEmpty() && !hasFocus()) {
                Graphics copy = graphics.create();
                copy.setFont(getFont());
                Color hint = UIManager.getColor("Label.disabledForeground");
                copy.setColor(hint == null ? getForeground() : hint);
                copy.drawString("Ask about your course or code…", getInsets().left,
                        getInsets().top + getFontMetrics(getFont()).getAscent());
                copy.dispose();
            }
        }
    };
    private final JLabel context = new JLabel();
    private final JLabel indexStatus = new JLabel("Checking index…");
    private final JLabel model = new JLabel();
    private final JButton send = new JButton("Send");
    private final JButton index = MarkdownContent.smallButton("Build index");
    private final JButton latest = MarkdownContent.smallButton("↓ Latest response");
    private final JPanel empty = new JPanel();
    private JPanel pending;
    private String codeContext;
    private String requestQuestion;
    private String requestCode;
    private boolean busy;
    private boolean building;
    private boolean followBottom = true;
    private boolean programmaticScroll;
    private boolean hasNewContent;
    private String indexDescription = "Checking index…";
    private boolean indexReady;
    private Font codeFont = new Font(Font.MONOSPACED, Font.PLAIN, 14);

    ChatView(BiConsumer<String, String> ask, Runnable buildIndex, Runnable settings, Consumer<String> openLink) {
        super(new BorderLayout(0, 0));
        this.ask = ask;
        this.buildIndex = buildIndex;
        this.settings = settings;
        this.openLink = openLink;
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        add(toolbar(), BorderLayout.NORTH);

        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        history.setBorder(null);
        history.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        history.getVerticalScrollBar().setUnitIncrement(22);
        history.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (!programmaticScroll) {
                JScrollBar bar = history.getVerticalScrollBar();
                followBottom = bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 28;
                if (followBottom) hasNewContent = false;
                latest.setText(hasNewContent ? "↓ New response" : "↓ Back to latest");
                latest.setVisible(!followBottom);
            }
        });
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(history, BorderLayout.CENTER);
        latest.setVisible(false);
        latest.addActionListener(event -> {
            followBottom = true;
            hasNewContent = false;
            scrollAfterChange(true, 0);
        });
        center.add(latest, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        add(composer(), BorderLayout.SOUTH);
        showEmpty();
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        index.addActionListener(event -> {
            if (!busy && !building) {
                building = true;
                updateControls();
                buildIndex.run();
            }
        });
        indexStatus.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        bar.add(index, BorderLayout.WEST);
        bar.add(indexStatus, BorderLayout.CENTER);
        JButton more = MarkdownContent.smallButton("···");
        more.setToolTipText("Chat options");
        more.getAccessibleContext().setAccessibleName("Chat options");
        JPopupMenu options = new JPopupMenu();
        JMenuItem configure = new JMenuItem("Settings");
        configure.addActionListener(event -> settings.run());
        JMenuItem clear = new JMenuItem("Clear conversation");
        clear.addActionListener(event -> {
            if (!busy && !building) {
                messages.removeAll();
                pending = null;
                followBottom = true;
                hasNewContent = false;
                latest.setVisible(false);
                showEmpty();
                messages.revalidate();
                messages.repaint();
            }
        });
        options.add(configure);
        options.add(clear);
        more.addActionListener(event -> {
            SwingUtilities.updateComponentTreeUI(options);
            clear.setEnabled(!busy && !building);
            configure.setEnabled(!busy && !building);
            options.show(more, 0, more.getHeight());
        });
        bar.add(more, BorderLayout.EAST);
        return bar;
    }

    private JPanel composer() {
        JPanel panel = new JPanel(new BorderLayout(0, 7));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        context.setVisible(false);
        context.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        panel.add(context, BorderLayout.NORTH);
        input.setFont(UIManager.getFont("Label.font"));
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        input.setToolTipText("Ask about the course or selected code. Enter to send; Shift+Enter for a new line.");
        input.getAccessibleContext().setAccessibleName("Message to CST3492 TA");
        input.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-message");
        input.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        input.getActionMap().put("send-message", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { submit(); }
        });
        input.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                int lines = Math.max(input.getLineCount(), input.getText().length() / 45 + 1);
                input.setRows(Math.min(7, Math.max(3, lines)));
                updateControls();
                panel.revalidate();
            }
            @Override public void insertUpdate(DocumentEvent e) { changed(); }
            @Override public void removeUpdate(DocumentEvent e) { changed(); }
            @Override public void changedUpdate(DocumentEvent e) { changed(); }
        });
        JScrollPane edit = new JScrollPane(input);
        edit.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(edit, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 3));
        footer.setOpaque(false);
        model.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        JLabel shortcut = new JLabel("Enter to send · Shift+Enter for a new line");
        shortcut.setFont(model.getFont());
        JPanel hints = new JPanel(new GridLayout(0, 1, 0, 2));
        hints.setOpaque(false);
        hints.add(model);
        hints.add(shortcut);
        footer.add(hints, BorderLayout.CENTER);
        send.addActionListener(event -> submit());
        footer.add(send, BorderLayout.EAST);
        panel.add(footer, BorderLayout.SOUTH);
        updateControls();
        return panel;
    }

    private void showEmpty() {
        empty.removeAll();
        empty.setLayout(new BoxLayout(empty, BoxLayout.Y_AXIS));
        empty.setOpaque(false);
        empty.setBorder(BorderFactory.createEmptyBorder(30, 6, 24, 6));
        empty.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel("Ask your course TA");
        title.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 17f));
        empty.add(title);
        empty.add(Box.createVerticalStrut(9));
        empty.add(MarkdownContent.plain("Understand Java concepts, explore course material,\nor ask about selected code."));
        for (String example : List.of("Explain dependency injection", "How do I test an empty input?")) {
            JButton suggestion = MarkdownContent.smallButton(example);
            suggestion.setAlignmentX(LEFT_ALIGNMENT);
            suggestion.addActionListener(event -> { input.setText(example); input.requestFocusInWindow(); });
            empty.add(Box.createVerticalStrut(7));
            empty.add(suggestion);
        }
        messages.add(empty);
    }

    void setModel(String name) {
        model.setText("Model · " + name);
        model.setToolTipText(name);
    }

    void setCodeFont(Font font) { codeFont = font; }

    void setIndexStatus(String text, boolean ready) {
        indexDescription = text;
        indexReady = ready;
        updateIndexDisplay();
    }

    private void updateIndexDisplay() {
        String text = building ? "Building course index…" : indexDescription;
        indexStatus.setText(text);
        indexStatus.setToolTipText(text);
        index.setText(indexReady ? "Rebuild index" : "Build index");
    }

    void finishIndex(String message, String error) {
        building = false;
        updateControls();
        changeMessages(() -> {
            JPanel card = card(error == null ? "COURSE INDEX" : "INDEX BUILD FAILED");
            card.add(MarkdownContent.plain(error == null ? message : friendlyError(error)), BorderLayout.CENTER);
            if (error != null) {
                JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                actions.setOpaque(false);
                JButton details = MarkdownContent.smallButton("Technical details");
                details.addActionListener(event -> JOptionPane.showMessageDialog(this,
                        new JScrollPane(new JTextArea(error, 8, 36)), "Index build details", JOptionPane.ERROR_MESSAGE));
                actions.add(details);
                card.add(actions, BorderLayout.SOUTH);
            }
            messages.add(card);
        });
    }

    private void submit() {
        if (busy || building || input.getText().isBlank()) return;
        String question = input.getText().trim();
        String code = codeContext;
        input.setText("");
        setCodeContext(null);
        startRequest(question, code, true);
    }

    /** Entry used by the editor's selected-code action. */
    void askQuestion(String question, String code) {
        if (busy || building) {
            JOptionPane.showMessageDialog(this, "Wait for the current task to finish, then ask about this selection again.",
                    "CST3492 TA", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // An editor action must not consume or replace an unrelated composer draft.
        startRequest(question, code, true);
    }

    private void startRequest(String question, String code, boolean newQuestion) {
        if (busy || building) return;
        busy = true;
        requestQuestion = question;
        requestCode = code;
        updateControls();
        changeMessages(() -> {
            if (newQuestion) {
                JPanel user = card("YOU");
                user.add(MarkdownContent.plain(question), BorderLayout.CENTER);
                if (code != null && !code.isBlank()) {
                    JButton selected = MarkdownContent.smallButton("Selected Java code · " + code.lines().count() + " lines");
                    selected.addActionListener(event -> JOptionPane.showMessageDialog(this,
                            new JScrollPane(new JTextArea(code, 12, 44)), "Code sent with this question", JOptionPane.PLAIN_MESSAGE));
                    user.add(selected, BorderLayout.SOUTH);
                }
                messages.add(user);
            } else if (pending != null) {
                messages.remove(pending);
            }
            pending = card("CST3492 TA");
            JPanel waiting = new JPanel(new BorderLayout(0, 8));
            waiting.setOpaque(false);
            waiting.add(new JLabel("Waiting for a response…"), BorderLayout.NORTH);
            JProgressBar progress = new JProgressBar();
            progress.setIndeterminate(true);
            progress.setPreferredSize(new Dimension(0, 3));
            waiting.add(progress, BorderLayout.SOUTH);
            pending.add(waiting, BorderLayout.CENTER);
            messages.add(pending);
        });
        ask.accept(question, code);
    }

    void answer(String text, List<KnowledgeChunk> sources, boolean grounded) {
        busy = false;
        updateControls();
        changeMessages(() -> {
            pending.removeAll();
            JPanel header = header("CST3492 TA");
            JButton copy = MarkdownContent.smallButton("Copy answer");
            copy.addActionListener(event -> MarkdownContent.copy(copy, text));
            header.add(copy, BorderLayout.EAST);
            pending.add(header, BorderLayout.NORTH);

            Map<String, KnowledgeChunk> unique = new LinkedHashMap<>();
            sources.forEach(source -> unique.putIfAbsent(source.citationLabel(), source));
            String numbered = text;
            JPanel sourceRows = new JPanel();
            sourceRows.setLayout(new BoxLayout(sourceRows, BoxLayout.Y_AXIS));
            sourceRows.setOpaque(false);
            int n = 0;
            for (KnowledgeChunk source : unique.values()) {
                n++;
                numbered = numbered.replace("[" + source.citationLabel() + "]", "[" + n + "](#source-" + n + ")");
                JEditorPane row = MarkdownContent.plain("[" + n + "] " + source.citationLabel());
                row.setToolTipText(source.citationLabel());
                sourceRows.add(row);
            }
            sourceRows.setVisible(false);
            JToggleButton toggle = new JToggleButton("Course sources · " + unique.size());
            toggle.setIcon(disclosureIcon(false));
            toggle.setSelectedIcon(disclosureIcon(true));
            toggle.setHorizontalAlignment(SwingConstants.LEFT);
            toggle.setMargin(new Insets(5, 0, 5, 0));
            toggle.setBorderPainted(false);
            toggle.setContentAreaFilled(false);
            toggle.addActionListener(event -> changeMessages(() -> {
                sourceRows.setVisible(toggle.isSelected());
            }));
            Consumer<String> links = url -> {
                if (url.startsWith("#source-")) {
                    toggle.setSelected(true);
                    changeMessages(() -> sourceRows.setVisible(true));
                    SwingUtilities.invokeLater(() -> sourceRows.scrollRectToVisible(
                            new Rectangle(0, 0, sourceRows.getWidth(), Math.min(60, sourceRows.getHeight()))));
                } else if (url.startsWith("https://") || url.startsWith("http://")) {
                    openLink.accept(url);
                }
            };
            pending.add(new MarkdownContent(numbered, links, codeFont), BorderLayout.CENTER);
            JPanel evidence = new JPanel(new BorderLayout(0, 4));
            evidence.setOpaque(false);
            if (!unique.isEmpty()) {
                evidence.add(toggle, BorderLayout.NORTH);
                evidence.add(sourceRows, BorderLayout.CENTER);
            } else if (!grounded) {
                evidence.add(MarkdownContent.plain("General knowledge · No course sources matched."), BorderLayout.CENTER);
            }
            pending.add(evidence, BorderLayout.SOUTH);
        });
    }

    void fail(String detail) {
        busy = false;
        updateControls();
        if (input.getText().isBlank()) {
            input.setText(requestQuestion);
            setCodeContext(requestCode);
        }
        changeMessages(() -> {
            pending.removeAll();
            pending.add(header("REQUEST FAILED"), BorderLayout.NORTH);
            JPanel content = new JPanel(new BorderLayout(0, 7));
            content.setOpaque(false);
            content.add(MarkdownContent.plain(friendlyError(detail)), BorderLayout.NORTH);
            JEditorPane details = MarkdownContent.plain(detail);
            details.setVisible(false);
            content.add(details, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            actions.setOpaque(false);
            JButton retry = MarkdownContent.smallButton("Retry");
            String failedQuestion = requestQuestion;
            String failedCode = requestCode;
            JPanel failedCard = pending;
            retry.addActionListener(event -> {
                if (busy || building) return;
                if (input.getText().trim().equals(failedQuestion)) {
                    input.setText("");
                    setCodeContext(null);
                }
                pending = failedCard;
                startRequest(failedQuestion, failedCode, false);
            });
            JButton configure = MarkdownContent.smallButton("Settings");
            configure.addActionListener(event -> settings.run());
            JButton technical = MarkdownContent.smallButton("Details");
            technical.addActionListener(event -> changeMessages(() -> details.setVisible(!details.isVisible())));
            actions.add(retry);
            actions.add(configure);
            actions.add(technical);
            content.add(actions, BorderLayout.SOUTH);
            pending.add(content, BorderLayout.CENTER);
        });
    }

    private void setCodeContext(String code) {
        codeContext = code;
        context.setText(code == null ? "" : "Selected Java code · " + code.lines().count() + " lines");
        context.setToolTipText(code == null ? null : "The selected code will be included when you send this question.");
        context.setVisible(code != null && !code.isBlank());
    }

    private void updateControls() {
        updateIndexDisplay();
        index.setEnabled(!busy && !building);
        send.setEnabled(!busy && !building && !input.getText().isBlank());
        send.setText(busy ? "Waiting…" : "Send");
    }

    private JPanel card(String role) {
        JPanel card = new JPanel(new BorderLayout(0, 7)) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(13, 2, 15, 2)));
        card.add(header(role), BorderLayout.NORTH);
        return card;
    }

    private JPanel header(String role) {
        JPanel header = new JPanel(new BorderLayout(5, 0));
        header.setOpaque(false);
        JLabel label = new JLabel(role);
        label.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 11f));
        header.add(label, BorderLayout.WEST);
        return header;
    }

    private void changeMessages(Runnable change) {
        boolean follow = followBottom;
        int position = history.getVerticalScrollBar().getValue();
        programmaticScroll = true;
        if (empty.getParent() == messages) messages.remove(empty);
        change.run();
        messages.revalidate();
        messages.repaint();
        hasNewContent = true;
        scrollAfterChange(follow, position);
    }

    private void scrollAfterChange(boolean follow, int position) {
        programmaticScroll = true;
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = history.getVerticalScrollBar();
            bar.setValue(follow ? bar.getMaximum() : position);
            followBottom = follow;
            latest.setText(hasNewContent ? "↓ New response" : "↓ Back to latest");
            latest.setVisible(!follow);
            programmaticScroll = false;
        });
    }

    private static String friendlyError(String detail) {
        if (detail.contains("502") || detail.contains("503"))
            return "The model service is temporarily unavailable. Your question is kept below; try again shortly.";
        if (detail.contains("401")) return "The service rejected the API key. Check the key in Settings.";
        if (detail.contains("index") || detail.contains("Index"))
            return "The course index is unavailable or needs rebuilding. Check Settings, then build the index.";
        if (detail.contains("key")) return "Configure the required API keys in Settings, then retry.";
        return "The request could not be completed. Your question is kept below; check the details or retry.";
    }

    private static Icon disclosureIcon(boolean expanded) {
        return new Icon() {
            @Override public int getIconWidth() { return 10; }
            @Override public int getIconHeight() { return 10; }
            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics copy = graphics.create();
                copy.setColor(component.getForeground());
                copy.translate(x, y);
                copy.fillPolygon(expanded ? new int[]{1, 9, 5} : new int[]{2, 8, 2},
                        expanded ? new int[]{3, 3, 8} : new int[]{1, 5, 9}, 3);
                copy.dispose();
            }
        };
    }

    private static final class MessageList extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(420, 600); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 22; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return r.height - 30; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
