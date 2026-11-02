package com.cst3492.ta.ui;

import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.service.TaProjectService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;

/**
 * The chat panel of the CST3492 TA tool window: build-index button, status line,
 * conversation area and the question field.
 */
public final class ChatPanel extends JPanel {

    private final Project project;
    private final JEditorPane conversation = new JEditorPane("text/html", "");
    private final StringBuilder transcript = new StringBuilder();
    private final JButton send = new JButton("Ask");
    private final JButton cancel = new JButton("Cancel");
    private com.cst3492.ta.util.BackgroundWork.Handle activeTask;
    private final JTextField questionField = new JTextField();
    private final JLabel status = new JLabel("Ready.");
    private final JButton buildButton = new JButton("Build Index");

    public ChatPanel(Project project) {
        super(new BorderLayout(0, 4));
        this.project = project;

        conversation.setEditable(false);

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.add(buildButton, BorderLayout.WEST);
        top.add(status, BorderLayout.CENTER);
        buildButton.addActionListener(e -> buildIndex());

        JPanel bottom = new JPanel(new BorderLayout(4, 0));
        bottom.add(questionField, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.add(send);
        controls.add(cancel);
        cancel.setEnabled(false);
        cancel.addActionListener(e -> {
            if (activeTask != null) { activeTask.cancel(); status.setText("Cancelling..."); }
        });
        bottom.add(controls, BorderLayout.EAST);
        send.addActionListener(e -> ask());
        questionField.addActionListener(e -> ask());

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(conversation), BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    }

    private void buildIndex() {
        TaSettingsState s = TaSettingsState.getInstance();
        if (TaSettingsState.getEmbeddingApiKey() == null || s.materialsPath.isBlank()) {
            Messages.showInfoMessage(project,
                    "Configure the embedding API key and the materials folder first:\n"
                            + "Settings > Tools > CST3492 TA.",
                    "CST3492 TA");
            return;
        }
        if (activeTask != null) return;
        setBusy(true);
        status.setText("Building index...");
        activeTask = com.cst3492.ta.util.BackgroundWork.run(project, "CST3492 TA: Building Index",
                indicator -> {
                    indicator.setIndeterminate(false);
                    return TaProjectService.getInstance(project).buildIndex(indicator);
                }, result -> {
                    status.setText("Index ready.");
                    append("System", String.format("Index built: %d built, %d skipped, %d new chunks from %d pages.",
                            result.documentsBuilt(), result.documentsSkipped(), result.chunkCount(), result.pageCount()));
                }, error -> { status.setText("Index build failed."); append("Error", error); }, this::taskFinished);
    }

    private void ask() {
        String question = questionField.getText().trim();
        if (question.isEmpty()) {
            return;
        }
        questionField.setText("");
        askQuestion(question, null);
    }

    public void askQuestion(String question, String codeContext) {
        if (activeTask != null) {
            Messages.showInfoMessage(project, "A TA task is running. Wait or cancel it first.", "CST3492 TA");
            return;
        }
        append(codeContext == null ? "You" : "You (with selected code)", question);
        if (codeContext != null) append("Selected code", "```java\n" + codeContext + "\n```");
        setBusy(true);
        status.setText("Retrieving and asking...");
        activeTask = com.cst3492.ta.util.BackgroundWork.run(project, "CST3492 TA: Answering",
                indicator -> TaProjectService.getInstance(project).ask(question, codeContext), answer -> {
                    append("TA", answer.text());
                    if (!answer.sources().isEmpty()) {
                        StringBuilder sources = new StringBuilder();
                        for (var source : answer.sources()) sources.append(source.citationLabel()).append('\n');
                        append("Sources", sources.toString());
                    }
                    status.setText("Ready.");
                }, error -> { append("Error", error); status.setText("Question failed."); }, this::taskFinished);
    }

    private void setBusy(boolean busy) {
        buildButton.setEnabled(!busy);
        send.setEnabled(!busy);
        questionField.setEnabled(!busy);
        cancel.setEnabled(busy);
    }

    private void taskFinished() {
        if (activeTask != null && activeTask.isCancelled()) status.setText("Cancelled.");
        activeTask = null;
        setBusy(false);
    }

    private void append(String who, String text) {
        transcript.append("<h3>").append(MarkdownView.escape(who)).append("</h3>")
                .append(MarkdownView.render(text == null ? "" : text));
        Color foreground = UIManager.getColor("Label.foreground");
        Color background = UIManager.getColor("Panel.background");
        String textColor = String.format("#%06x", (foreground == null ? Color.BLACK : foreground).getRGB() & 0xffffff);
        String backgroundColor = String.format("#%06x", (background == null ? Color.WHITE : background).getRGB() & 0xffffff);
        conversation.setText("<html><body style='font-family:sans-serif;color:" + textColor
                + ";background-color:" + backgroundColor + "'>" + transcript + "</body></html>");
        conversation.setCaretPosition(conversation.getDocument().getLength());
    }
}
