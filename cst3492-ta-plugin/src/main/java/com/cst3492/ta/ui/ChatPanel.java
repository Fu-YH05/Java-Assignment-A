package com.cst3492.ta.ui;

import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.service.TaProjectService;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The chat panel of the CST3492 TA tool window: build-index button, status line,
 * conversation area and the question field.
 */
public final class ChatPanel extends JPanel {

    private final Project project;
    private final JTextArea conversation = new JTextArea();
    private final JTextField questionField = new JTextField();
    private final JLabel status = new JLabel("Ready.");
    private final JButton buildButton = new JButton("Build Index");

    public ChatPanel(Project project) {
        super(new BorderLayout(0, 4));
        this.project = project;

        conversation.setEditable(false);
        conversation.setLineWrap(true);
        conversation.setWrapStyleWord(true);

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.add(buildButton, BorderLayout.WEST);
        top.add(status, BorderLayout.CENTER);
        buildButton.addActionListener(e -> buildIndex());

        JPanel bottom = new JPanel(new BorderLayout(4, 0));
        bottom.add(questionField, BorderLayout.CENTER);
        JButton send = new JButton("Ask");
        bottom.add(send, BorderLayout.EAST);
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
        buildButton.setEnabled(false);
        status.setText("Building index...");
        new Task.Backgroundable(project, "CST3492 TA: Building Index", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    TaProjectService.BuildResult result =
                            TaProjectService.getInstance(project).buildIndex(indicator);
                    SwingUtilities.invokeLater(() -> {
                        status.setText(String.format("Index ready: %d built, %d skipped, %d chunks.",
                                result.documentsBuilt(), result.documentsSkipped(), result.chunkCount()));
                        append("System", String.format(
                                "Index built: %d document(s) built, %d skipped, %d new chunks from %d pages.",
                                result.documentsBuilt(), result.documentsSkipped(),
                                result.chunkCount(), result.pageCount()));
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        status.setText("Index build failed.");
                        append("Error", ex.getMessage());
                    });
                } finally {
                    SwingUtilities.invokeLater(() -> buildButton.setEnabled(true));
                }
            }
        }.queue();
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
        append("You", question);
        status.setText("Asking...");
        AppExecutorUtil.getAppExecutorService().submit(() -> {
            try {
                TaProjectService.Answer answer =
                        TaProjectService.getInstance(project).ask(question, codeContext);
                SwingUtilities.invokeLater(() -> {
                    append("TA", answer.text());
                    if (!answer.sources().isEmpty()) {
                        StringBuilder sb = new StringBuilder("Sources:\n");
                        List<com.cst3492.ta.model.KnowledgeChunk> seen = answer.sources();
                        for (int i = 0; i < seen.size(); i++) {
                            sb.append("  ").append(i + 1).append(". ")
                                    .append(seen.get(i).citationLabel()).append('\n');
                        }
                        append("", sb.toString());
                    }
                    status.setText("Ready.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    append("Error", ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    status.setText("Ready.");
                });
            }
        });
    }

    private void append(String who, String text) {
        if (!who.isEmpty()) {
            conversation.append(who + ":\n");
        }
        conversation.append(text.strip() + "\n\n");
        conversation.setCaretPosition(conversation.getDocument().getLength());
    }
}
