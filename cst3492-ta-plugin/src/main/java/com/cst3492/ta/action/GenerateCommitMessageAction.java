package com.cst3492.ta.action;

import com.cst3492.ta.llm.LlmClient;
import com.cst3492.ta.llm.Message;
import com.cst3492.ta.service.TaProjectService;
import com.cst3492.ta.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * F1 action 2: draft a conventional commit message from the staged diff. The action analyses
 * only staged changes and never commits automatically.
 */
public final class GenerateCommitMessageAction extends AnAction {

    private static final int MAX_DIFF_CHARS = 8000;

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null
                && e.getProject().getBasePath() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null || project.getBasePath() == null) {
            return;
        }
        String diff = stagedDiff(project.getBasePath());
        if (diff.isBlank()) {
            Messages.showInfoMessage(project,
                    "No staged changes found. Stage your changes first; this action analyses "
                            + "staged changes only.",
                    "CST3492 TA");
            return;
        }
        new Thread(() -> {
            try {
                LlmClient client = TaProjectService.getInstance(project).llmClient();
                String system = """
                        You write Conventional Commits messages.
                        Respond with ONLY a JSON object: {"type":"...","scope":"..." or null,\
                        "subject":"...","body":"..."}
                        Rules: type in {feat, fix, refactor, test, docs, chore}; subject is imperative,\
                        max 72 chars, no period; body explains why, max 3 bullet lines separated by \\n.
                        Do not speculate about changes that are not in the diff.
                        """;
                String user = "Staged diff (possibly truncated):\n```diff\n"
                        + truncate(diff) + "\n```";
                String json = client.completeJson(
                        List.of(new Message("system", system), new Message("user", user)),
                        0.1, 300);
                JsonNode node = JsonUtil.mapper().readTree(json);
                String type = node.path("type").asText("chore");
                String scope = node.path("scope").isMissingNode() || node.path("scope").isNull()
                        ? null : node.path("scope").asText();
                String subject = node.path("subject").asText("");
                String body = node.path("body").asText("");
                SwingUtilities.invokeLater(() ->
                        new CommitDialog(project, type, scope, subject, body).show());
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog(
                        project, ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                        "CST3492 TA: Commit Message Failed"));
            }
        }).start();
    }

    static String stagedDiff(String basePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", "--cached", "--unified=2");
            pb.directory(new java.io.File(basePath));
            pb.environment().put("GIT_PAGER", "cat");
            Process process = pb.start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (var in = process.getInputStream()) {
                in.transferTo(out);
            }
            if (process.waitFor() != 0) {
                return "";
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String truncate(String diff) {
        return diff.length() > MAX_DIFF_CHARS
                ? diff.substring(0, MAX_DIFF_CHARS) + "\n... (truncated)" : diff;
    }

    private static final class CommitDialog extends DialogWrapper {
        private final JTextArea area = new JTextArea(12, 60);

        CommitDialog(Project project, String type, String scope, String subject, String body) {
            super(project);
            setTitle("Generated Commit Message");
            setOKButtonText("Copy");
            init();
            StringBuilder sb = new StringBuilder(type);
            if (scope != null && !scope.isBlank()) {
                sb.append('(').append(scope).append(')');
            }
            sb.append(": ").append(subject);
            if (!body.isBlank()) {
                sb.append("\n\n").append(body);
            }
            area.setText(sb.toString());
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.add(new JLabel("Editable draft — review before committing:"), BorderLayout.NORTH);
            panel.add(new JScrollPane(area), BorderLayout.CENTER);
            return panel;
        }

        @Override
        protected void doOKAction() {
            area.selectAll();
            area.copy();
            super.doOKAction();
        }
    }
}
