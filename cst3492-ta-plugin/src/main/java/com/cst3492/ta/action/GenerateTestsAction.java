package com.cst3492.ta.action;

import com.cst3492.ta.llm.LlmClient;
import com.cst3492.ta.llm.Message;
import com.cst3492.ta.service.TaProjectService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

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
        String psiContext = editor == null ? null : editor.getDocument().getText();
        String selectedContext = readSelectedCode(editor);
        com.intellij.openapi.module.Module module = e.getData(com.intellij.openapi.actionSystem.LangDataKeys.MODULE);

        com.cst3492.ta.util.BackgroundWork.run(project, "CST3492 TA: Generate Tests", indicator -> {
            LlmClient client = TaProjectService.getInstance(project).llmClient();
            String system = """
                    You generate JUnit 5 tests for Java classes.
                    Return only Java source code: one complete test class with package and imports.
                    Use JUnit 5 (org.junit.jupiter.api), assertions from org.junit.jupiter.api.Assertions.
                    Cover the given scenario including boundary cases. Do not invent dependencies.
                    """;
            String user = "Class under test:\n```java\n"
                    + (psiContext == null || psiContext.isBlank()
                    ? "(not available; infer a reasonable class from the scenario)" : psiContext)
                    + "\n```\nSelected method or code:\n" + selectedContext + "\nTest scenario: " + scenario;
            String code = client.complete(
                    List.of(new Message("system", system), new Message("user", user)),
                    0.1, 2000);
            String className = firstClassName(code, "GeneratedTest");
            return new GeneratedDraft(code, className);
        }, result -> new PreviewDialog(project, module, result.code(), result.className()).show());
    }

    private record GeneratedDraft(String code, String className) {}

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

    /** Saves a new file under the chosen test source root; never overwrites an existing file. */
    private static final class PreviewDialog extends DialogWrapper {
        private final Project project;
        private final com.intellij.openapi.module.Module module;
        private final JTextArea area = new JTextArea(24, 72);

        PreviewDialog(Project project, com.intellij.openapi.module.Module module, String code, String className) {
            super(project);
            this.project = project;
            this.module = module;
            setTitle("Generated Test: " + className);
            setOKButtonText("Create Test File");
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
            try {
                if (createTestFile()) super.doOKAction();
            } catch (Exception ex) {
                Messages.showErrorDialog(project, ex.getMessage(), "Cannot Create Test File");
            }
        }

        private boolean createTestFile() {
            var draft = com.cst3492.ta.util.GeneratedTestSource.parse(area.getText());
            var parsed = com.intellij.psi.PsiFileFactory.getInstance(project).createFileFromText(
                    draft.className() + ".java", com.intellij.ide.highlighter.JavaFileType.INSTANCE, draft.code());
            if (!(parsed instanceof com.intellij.psi.PsiJavaFile javaFile)
                    || com.intellij.psi.util.PsiTreeUtil.findChildOfType(parsed, com.intellij.psi.PsiErrorElement.class) != null
                    || javaFile.getClasses().length == 0) {
                throw new IllegalArgumentException("Generated Java has syntax errors. Edit the preview before saving.");
            }
            var publicTypes = java.util.Arrays.stream(javaFile.getClasses())
                    .filter(c -> c.hasModifierProperty(com.intellij.psi.PsiModifier.PUBLIC)).toList();
            if (publicTypes.size() > 1) throw new IllegalArgumentException("Multiple public top-level classes are not allowed.");
            var targetClass = publicTypes.isEmpty() ? javaFile.getClasses()[0] : publicTypes.get(0);
            if (targetClass.isInterface() || targetClass.isEnum() || targetClass.isRecord()) {
                throw new IllegalArgumentException("Expected a Java test class.");
            }
            var source = new com.cst3492.ta.util.GeneratedTestSource(draft.code(), javaFile.getPackageName(), targetClass.getName());
            java.util.List<com.intellij.openapi.vfs.VirtualFile> roots = new java.util.ArrayList<>();
            if (module != null) {
                for (var root : com.intellij.openapi.roots.ModuleRootManager.getInstance(module).getSourceRoots(true)) {
                    if (com.intellij.openapi.roots.ProjectRootManager.getInstance(project).getFileIndex()
                            .isInTestSourceContent(root)) roots.add(root);
                }
            }
            String defaultRoot = roots.size() == 1 ? roots.get(0).getPath()
                    : project.getBasePath() + "/src/test/java";
            String path = Messages.showInputDialog(project,
                    "Test source root (file: " + source.relativePath() + "):",
                    "Choose Test Source Folder", null, defaultRoot, null);
            if (path == null || path.isBlank()) return false;
            java.nio.file.Path rootPath = java.nio.file.Path.of(path).toAbsolutePath().normalize();
            java.nio.file.Path projectPath = java.nio.file.Path.of(project.getBasePath()).toAbsolutePath().normalize();
            if (!rootPath.startsWith(projectPath)) {
                throw new IllegalArgumentException("Choose a test source folder inside this project.");
            }
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    var root = com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing(rootPath.toString());
                    if (root == null) throw new IllegalStateException("Cannot create test folder.");
                    var directory = root;
                    if (!source.packageName().isEmpty()) {
                        for (String part : source.packageName().split("\\.")) {
                            var child = directory.findChild(part);
                            directory = child == null ? directory.createChildDirectory(this, part) : child;
                        }
                    }
                    String name = source.className() + ".java";
                    if (directory.findChild(name) != null) throw new IllegalArgumentException("File already exists: " + name);
                    var file = directory.createChildData(this, name);
                    com.intellij.openapi.vfs.VfsUtil.saveText(file, source.code() + "\n");
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true);
                } catch (java.io.IOException ex) {
                    throw new IllegalStateException("Cannot save test file: " + ex.getMessage(), ex);
                }
            });
            return true;
        }
    }
}
