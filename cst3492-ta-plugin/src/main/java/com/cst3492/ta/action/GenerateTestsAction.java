package com.cst3492.ta.action;

import com.cst3492.ta.ui.ChatPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

/** Capture context now; the required natural-language scenario is entered in a new test conversation. */
public final class GenerateTestsAction extends AnAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.EDT; }
    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && event.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile);
    }

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null || !(event.getData(CommonDataKeys.PSI_FILE) instanceof PsiJavaFile file)
                || file.getVirtualFile() == null) return;
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
        String source = editor.getDocument().getText();
        String selected = editor.getSelectionModel().getSelectedText();
        if (selected == null || selected.isBlank()) selected = source;
        String snapshotSelection = selected;
        String path = file.getVirtualFile().getPath();
        String packageName = file.getPackageName();
        var window = ToolWindowManager.getInstance(project).getToolWindow("CST3492 TA");
        if (window == null) {
            Messages.showErrorDialog(project, "The TA tool window is unavailable.", "Generate Tests");
            return;
        }
        window.activate(() -> {
            for (var content : window.getContentManager().getContents()) {
                if (content.getComponent() instanceof ChatPanel panel) {
                    panel.startTestConversation(path, packageName, snapshotSelection, source);
                    return;
                }
            }
        });
    }
}