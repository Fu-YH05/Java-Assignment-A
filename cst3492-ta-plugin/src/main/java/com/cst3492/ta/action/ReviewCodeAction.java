package com.cst3492.ta.action;

import com.cst3492.ta.llm.LlmClient;
import com.cst3492.ta.citation.CitationVerifier;
import com.cst3492.ta.llm.Message;
import com.cst3492.ta.model.KnowledgeChunk;
import com.cst3492.ta.service.TaProjectService;
import com.cst3492.ta.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Creative F1 action: course-aware code review. The selected code is reviewed and each finding
 * is grounded in the lecture material, so the plugin explains problems in the terminology of
 * the course rather than giving generic style warnings.
 */
public final class ReviewCodeAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null
                && editor != null
                && editor.getSelectionModel().getSelectedText() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        String code = editor.getSelectionModel().getSelectedText();
        com.cst3492.ta.util.BackgroundWork.run(project, "CST3492 TA: Review Code", indicator -> {
                List<KnowledgeChunk> courseChunks =
                        TaProjectService.getInstance(project).retrieveForContext(code);
                LlmClient client = TaProjectService.getInstance(project).llmClient();
                String system = """
                        You are a code reviewer for CST3492 Java Enterprise Application Development.
                        Review the given code and respond with ONLY a JSON object:
                        {"findings":[{"line":"approximate location or name","severity":\
                        "critical|major|minor","problem":"...","explanation":"... why, in course \
                        terminology","suggestion":"...","citation":"docId#page" or null}]}
                        Ground explanations in the provided course excerpts when possible and set \
                        citation to the matching docId#page. Never invent course references.
                        """;
                StringBuilder excerpts = new StringBuilder();
                for (int i = 0; i < courseChunks.size(); i++) {
                    KnowledgeChunk c = courseChunks.get(i);
                    excerpts.append("[docId=").append(c.docId).append(", page=").append(c.pageFrom)
                            .append("] ").append(c.text).append("\n\n");
                }
                String user = "Course excerpts:\n" + (courseChunks.isEmpty()
                        ? "(no course material matched; use general knowledge and null citations)"
                        : excerpts.toString())
                        + "Code to review:\n```java\n" + code + "\n```";
                String json = client.completeJson(
                        List.of(new Message("system", system), new Message("user", user)),
                        0.2, 1500);
                JsonNode findings = JsonUtil.mapper().readTree(json).path("findings");
                if (!findings.isArray()) throw new IllegalStateException("Invalid review response: findings must be an array.");
                return new ReviewResult(findings, courseChunks);
        }, result -> new FindingsDialog(project, result.findings(), result.sources()).show());
    }

    private record ReviewResult(JsonNode findings, List<KnowledgeChunk> sources) {}

    private static final class FindingsDialog extends DialogWrapper {
        private final JTextArea area = new JTextArea(18, 66);

        FindingsDialog(Project project, JsonNode findings, List<KnowledgeChunk> sources) {
            super(project);
            setTitle("Course-Aware Code Review");
            setOKButtonText("Close");
            init();
            StringBuilder sb = new StringBuilder();
            if (!findings.isArray() || findings.isEmpty()) {
                sb.append("No issues found in the selected code.");
            } else {
                int n = 1;
                for (JsonNode f : findings) {
                    sb.append(n++).append(". [").append(f.path("severity").asText("minor"))
                            .append("] ").append(f.path("line").asText("")).append('\n');
                    sb.append("   Problem: ").append(f.path("problem").asText("")).append('\n');
                    sb.append("   Explanation: ").append(f.path("explanation").asText("")).append('\n');
                    sb.append("   Suggestion: ").append(f.path("suggestion").asText("")).append('\n');
                    JsonNode citation = f.path("citation");
                    if (!citation.isMissingNode() && !citation.isNull()
                            && !citation.asText().isBlank()) {
                        String label = CitationVerifier.sourceLabel(citation.asText(), sources);
                        sb.append(label == null ? "   Source: Unverified reference removed."
                                : "   Source: " + label).append('\n');
                    } else {
                        sb.append("   Source: General knowledge; no verified course reference.\n");
                    }
                    sb.append('\n');
                }
            }
            if (sources.isEmpty()) sb.insert(0, com.cst3492.ta.prompt.PromptBuilder.GENERAL_DISCLAIMER + "\n\n");
            area.setText(sb.toString());
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            return new JScrollPane(area);
        }
    }
}
