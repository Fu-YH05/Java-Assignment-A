package com.cst3492.ta.action;

import com.cst3492.ta.ui.ChatPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/** Sends a user-authored question and a snapshot of the selected code to the TA window. */
public final class AskSelectedCodeAction extends AnAction {
    @Override
    public void update(@NotNull AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        event.getPresentation().setEnabledAndVisible(event.getProject() != null
                && editor != null && editor.getSelectionModel().hasSelection());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) return;
        String code = editor.getSelectionModel().getSelectedText();
        if (code == null || code.isBlank()) return;
        String question = Messages.showInputDialog(project, "What would you like to ask about this code?",
                "Ask TA about Selected Code", Messages.getQuestionIcon());
        if (question == null || question.isBlank()) return;
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("CST3492 TA");
        if (window == null) {
            Messages.showErrorDialog(project, "The CST3492 TA tool window is unavailable.", "CST3492 TA");
            return;
        }
        window.activate(() -> {
            for (var content : window.getContentManager().getContents()) {
                if (content.getComponent() instanceof ChatPanel panel) {
                    panel.askQuestion(question.trim(), code);
                    return;
                }
            }
        });
    }
}
