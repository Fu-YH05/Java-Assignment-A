package com.cst3492.ta.action;

import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.llm.LlmClient;
import com.cst3492.ta.llm.Message;
import com.cst3492.ta.service.TaProjectService;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * F1 action 1: generate a JUnit 5 test from a natural-language scenario. The dialog is
 * mandatory per the assignment: the user must type the scenario before anything is sent.
 */
public final class GenerateTestsAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null
                && e.getData(CommonDataKeys.EDITOR) != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (project == null) {
            return;
        }
        ScenarioDialog dialog = new ScenarioDialog(project);
        if (!dialog.showAndGet()) {
            return;
        }
        String scenario = dialog.getScenario();
        String psiContext = readSelectedCode(editor);

        new Thread(() -> {
            try {
                LlmClient client = TaProjectService.getInstance(project).llmClient();
                TaSettingsState s = TaSettingsState.getInstance();
                String system = """
                        You generate JUnit 5 tests for Java classes.
                        Return only Java source code: one complete test class with package and imports.
                        Use JUnit 5 (org.junit.jupiter.api), assertions from org.junit.jupiter.api.Assertions.
                        Cover the given scenario including boundary cases. Do not invent dependencies.
                        """;
                String user = "Class under test:\n```java\n"
                        + (psiContext == null || psiContext.isBlank()
                        ? "(not available; infer a reasonable class from the scenario)" : psiContext)
                        + "\n```\nTest scenario: " + scenario;
                String code = client.complete(
                        List.of(new Message("system", system), new Message("user", user)),
                        0.1, 2000);
                String className = firstClassName(code, "GeneratedTest");
                SwingUtilities.invokeLater(() -> new PreviewDialog(project, code, className).show());
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> Messages.showErrorDialog(
                        project, ex.getMessage() == null ? ex.toString() : ex.getMessage(),
                        "CST3492 TA: Test Generation Failed"));
            }
        }).start();
    }

    private static String readSelectedCode(Editor editor) {
        if (editor == null) {
            return null;
        }
        String selected = editor.getSelectionModel().getSelectedText();
        if (selected != null && !selected.isBlank()) {
            return selected;
        }
        return editor.getDocument().getText();
    }

    static String firstClassName(String code, String fallback) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("class\\s+([A-Za-z0-9_]+)").matcher(code);
        return m.find() ? m.group(1) : fallback;
    }

    private static final class ScenarioDialog extends DialogWrapper {
        private final JTextArea area = new JTextArea(8, 50);

        ScenarioDialog(Project project) {
            super(project);
            setTitle("Generate Tests from Requirement");
            init();
            area.setText("");
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.add(new JLabel("Describe the test scenario in natural language:"), BorderLayout.NORTH);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            panel.add(new JScrollPane(area), BorderLayout.CENTER);
            return panel;
        }

        @Override
        protected ValidationInfo doValidate() {
            return area.getText().isBlank()
                    ? new ValidationInfo("The scenario is required.") : null;
        }

        String getScenario() {
            return area.getText().trim();
        }
    }

    /** Preview with Copy and Insert-at-caret actions; production code is never modified. */
    private static final class PreviewDialog extends DialogWrapper {
        private final Project project;
        private final JTextArea area = new JTextArea(24, 72);
        private final String className;

        PreviewDialog(Project project, String code, String className) {
            super(project);
            this.project = project;
            this.className = className;
            setTitle("Generated Test: " + className);
            setOKButtonText("Insert at Caret");
            init();
            area.setText(code);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            return new JScrollPane(area);
        }

        @Override
        protected Action[] createActions() {
            Action insert = getOKAction();
            Action copy = new AbstractAction("Copy") {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    area.selectAll();
                    area.copy();
                }
            };
            return new Action[]{copy, insert, getCancelAction()};
        }

        @Override
        protected void doOKAction() {
            doInsert(project, area.getText());
            super.doOKAction();
        }

        private void doInsert(Project p, String code) {
            Editor editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(p)
                    .getSelectedTextEditor();
            if (editor == null) {
                return;
            }
            Document document = editor.getDocument();
            WriteCommandAction.runWriteCommandAction(p, () -> {
                int offset = editor.getCaretModel().getOffset();
                document.insertString(offset, "\n" + code + "\n");
                PsiDocumentManager.getInstance(p).commitDocument(document);
            });
        }
    }
}
