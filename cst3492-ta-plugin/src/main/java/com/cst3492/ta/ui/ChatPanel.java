package com.cst3492.ta.ui;

import com.cst3492.ta.config.TaSettingsConfigurable;
import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.service.TaProjectService;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/** Connects the conversation view to project services. All model/index work stays off the EDT. */
public final class ChatPanel extends JPanel {
    private final Project project;
    private final ChatView view;

    public ChatPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        view = new ChatView(this::ask, this::buildIndex, this::openSettings, BrowserUtil::browse);
        view.setCodeFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
        add(view, BorderLayout.CENTER);
        refreshIndexStatus();
    }

    private void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, TaSettingsConfigurable.class);
        refreshIndexStatus();
    }

    private void refreshIndexStatus() {
        view.setModel(TaSettingsState.getInstance().chatModel);
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                TaProjectService.IndexStatus status = TaProjectService.getInstance(project).indexStatus();
                onUi(() -> view.setIndexStatus(status.description(), status.ready()));
            } catch (Exception ex) {
                onUi(() -> view.setIndexStatus("Index could not be loaded", false));
            }
        });
    }

    private void buildIndex() {
        new Task.Backgroundable(project, "CST3492 TA: Building Index", false) {
            @Override public void run(@NotNull ProgressIndicator indicator) {
                try {
                    TaProjectService.BuildResult result = TaProjectService.getInstance(project).buildIndex(indicator);
                    onUi(() -> view.finishIndex(String.format(
                            "%d document(s) updated, %d unchanged. The course index is ready.",
                            result.documentsBuilt(), result.documentsSkipped()), null));
                } catch (Exception ex) {
                    onUi(() -> view.finishIndex(null, errorText(ex)));
                } finally {
                    onUi(ChatPanel.this::refreshIndexStatus);
                }
            }
        }.queue();
    }

    public void askQuestion(String question, String codeContext) {
        if (SwingUtilities.isEventDispatchThread()) view.askQuestion(question, codeContext);
        else onUi(() -> view.askQuestion(question, codeContext));
    }

    private void ask(String question, String codeContext) {
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            try {
                TaProjectService.Answer answer = TaProjectService.getInstance(project).ask(question, codeContext);
                onUi(() -> view.answer(answer.text(), answer.sources(), answer.grounded()));
            } catch (Exception ex) {
                onUi(() -> view.fail(errorText(ex)));
            }
        });
    }

    private void onUi(Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!project.isDisposed()) action.run();
        });
    }

    private static String errorText(Exception error) {
        return error.getMessage() == null ? error.toString() : error.getMessage();
    }
}
