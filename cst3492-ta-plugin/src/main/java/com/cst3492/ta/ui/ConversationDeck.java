package com.cst3492.ta.ui;

import com.cst3492.ta.model.Conversation;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** One tool window, independent views: callbacks keep their original view even while another is visible. */
final class ConversationDeck extends JPanel {
    private final Conversation.History history;
    private final Function<Conversation, ChatView> factory;
    private final Runnable changed;
    private final JPanel content = new JPanel(new CardLayout());
    private final Map<String, ChatView> views = new HashMap<>();
    private final JLabel title = new JLabel();
    private final JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final JLabel saveStatus = new JLabel();
    private final JPanel historyPanel = new JPanel(new BorderLayout(0, 8));
    private final JTextField search = new JTextField();
    private final DefaultListModel<Conversation> rows = new DefaultListModel<>();
    private final JList<Conversation> list = new JList<>(rows);
    private boolean updatingList;
    private boolean showingHistory;

    ConversationDeck(Conversation.History history, Function<Conversation, ChatView> factory, Runnable changed) {
        super(new BorderLayout());
        this.history = history;
        this.factory = factory;
        this.changed = changed;
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        title.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
        title.setMinimumSize(new Dimension(0, title.getPreferredSize().height));
        JButton showHistory = MarkdownContent.toolButton("History", "Conversation history");
        showHistory.addActionListener(event -> {
            if (showingHistory) show(history.active());
            else {
                showingHistory = true;
                filter();
                ((CardLayout) content.getLayout()).show(content, "history");
                refreshTitle();
            }
        });
        JButton newChat = MarkdownContent.toolButton("＋", "New chat");
        newChat.addActionListener(event -> newChat());
        tools.add(showHistory);
        tools.add(newChat);
        header.add(tools, BorderLayout.EAST);
        header.add(title, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);
        historyPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        search.getAccessibleContext().setAccessibleName("Search conversations");
        search.setToolTipText("Search conversation titles or test scenarios");
        JPanel searchBox = new JPanel(new BorderLayout(0, 4));
        searchBox.add(new JLabel("Search conversations"), BorderLayout.NORTH);
        searchBox.add(search, BorderLayout.CENTER);
        historyPanel.add(searchBox, BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                   boolean selected, boolean focus) {
                JLabel row = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                Conversation c = (Conversation) value;
                String time = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(c.createdAt));
                String summary = c.turns.isEmpty() ? c.draft : c.turns.getFirst().question;
                row.setText("<html><b>" + (c.testTask ? "Test · " : "Chat · ") + MarkdownContent.escape(c.title)
                        + "</b><br>" + time + " · " + c.turns.size() + " request(s)"
                        + (c.draft.isBlank() ? "" : " · Unsent draft")
                        + (c.testTask ? "<br>" + MarkdownContent.escape(summary.substring(0, Math.min(55, summary.length()))) : "") + "</html>");
                row.setBorder(BorderFactory.createEmptyBorder(10, 7, 10, 7));
                row.setToolTipText(c.title + " — " + summary);
                return row;
            }
        });
        list.addListSelectionListener(event -> {
            if (!updatingList && !event.getValueIsAdjusting() && list.getSelectedValue() != null)
                show(list.getSelectedValue());
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
        historyPanel.add(new JScrollPane(list), BorderLayout.CENTER);
        historyPanel.add(new JLabel("This project · Messages and drafts stay with their conversation"), BorderLayout.SOUTH);
        content.add(historyPanel, "history");
        content.setName("Conversation pages");
        add(content, BorderLayout.CENTER);
        saveStatus.setBorder(BorderFactory.createEmptyBorder(3, 12, 5, 12));
        saveStatus.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        add(saveStatus, BorderLayout.SOUTH);
        if (history.active() == null) history.newChat();
        show(history.active());
    }

    private void filter() {
        updatingList = true;
        rows.clear();
        String query = search.getText().toLowerCase(java.util.Locale.ROOT);
        history.conversations.stream().filter(Conversation::belongsToHistory).filter(c -> (c.title + " "
                + (c.turns.isEmpty() ? c.draft : c.turns.getFirst().question)).toLowerCase(java.util.Locale.ROOT).contains(query))
                .forEach(rows::addElement);
        updatingList = false;
    }

    void show(Conversation c) {
        history.activeId = c.id;
        if (!views.containsKey(c.id)) {
            ChatView view = factory.apply(c);
            views.put(c.id, view);
            content.add(view, c.id);
        }
        showingHistory = false;
        if (tools.getComponentCount() > 2) tools.remove(2);
        tools.add(views.get(c.id).takeOptionsButton());
        tools.revalidate();
        tools.repaint();
        ((CardLayout) content.getLayout()).show(content, c.id);
        refreshTitle();
        changed.run();
    }

    void refreshTitle() {
        if (showingHistory) {
            title.setText("Conversation history");
            title.setToolTipText("Conversation history");
            filter();
            return;
        }
        Conversation c = history.active();
        if (c == null) return;
        title.setText(c.title);
        title.setToolTipText(c.title);
    }

    void newChat() { show(history.newChat()); }
    void addTest(Conversation conversation) { show(history.add(conversation)); }
    ChatView activeView() { return views.get(history.activeId); }
    void saveStatus(String text) { saveStatus.setText(text); saveStatus.setToolTipText(text); }
}
