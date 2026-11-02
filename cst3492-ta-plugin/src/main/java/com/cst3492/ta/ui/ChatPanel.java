package com.cst3492.ta.ui;

import com.cst3492.ta.config.TaSettingsConfigurable;
import com.cst3492.ta.config.TaSettingsState;
import com.cst3492.ta.service.TaProjectService;
import com.cst3492.ta.util.BackgroundWork;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.concurrency.AppExecutorUtil;

import javax.swing.*;
import java.awt.*;

/** Connects the conversation view to project services. All model/index work stays off the EDT. */
public final class ChatPanel extends JPanel {
    private final Project project;
    private final ChatView view;
    private BackgroundWork.Handle activeTask;

    public ChatPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        view = new ChatView(this::ask, this::buildIndex, this::openSettings, BrowserUtil::browse);
        view.setCodeFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
        view.setCancelHandler(this::cancelActiveTask);
        add(view, BorderLayout.CENTER);
        refreshIndexStatus();
    }

    private void cancelActiveTask() {
        BackgroundWork.Handle handle = activeTask;
        if (handle != null) handle.cancel();
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
        if (activeTask != null) return;
        TaSettingsState settings = TaSettingsState.getInstance();
        // PasswordSafe lookups are slow operations, so the key check must stay off the EDT.
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            boolean hasKey = TaSettingsState.getEmbeddingApiKey() != null;
            onUi(() -> {
                if (!hasKey || settings.materialsPath.isBlank()) {
                    Messages.showInfoMessage(project,
                            "Configure the embedding API key and the materials folder first:\n"
                                    + "Settings > Tools > CST3492 TA.",
                            "CST3492 TA");
                    view.finishIndex(null, "Configure the embedding API key and the materials folder first, then build the index.");
                    return;
                }
                startIndexBuild();
            });
        });
    }

    private void startIndexBuild() {
        if (activeTask != null) return;
        BackgroundWork.Handle[] holder = new BackgroundWork.Handle[1];
        holder[0] = BackgroundWork.run(project, "CST3492 TA: Building Index",
                indicator -> TaProjectService.getInstance(project).buildIndex(indicator),
                result -> view.finishIndex(String.format(
                        "%d document(s) updated, %d unchanged. The course index is ready.",
                        result.documentsBuilt(), result.documentsSkipped()), null),
                error -> view.finishIndex(null, error),
                () -> {
                    activeTask = null;
                    if (holder[0].isCancelled()) view.finishIndex(null, "Index build cancelled.");
                    refreshIndexStatus();
                });
        activeTask = holder[0];
    }

    public void askQuestion(String question, String codeContext) {
        if (SwingUtilities.isEventDispatchThread()) view.askQuestion(question, codeContext);
        else onUi(() -> view.askQuestion(question, codeContext));
    }

    private void ask(String question, String codeContext) {
        if (activeTask != null) return;
        BackgroundWork.Handle[] holder = new BackgroundWork.Handle[1];
        holder[0] = BackgroundWork.run(project, "CST3492 TA: Answering",
                indicator -> TaProjectService.getInstance(project).ask(question, codeContext),
                answer -> view.answer(answer.text(), answer.sources(), answer.grounded()),
                error -> view.fail(error),
                () -> {
                    activeTask = null;
                    if (holder[0].isCancelled()) view.cancelled();
                });
        activeTask = holder[0];
    }

    private void onUi(Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!project.isDisposed()) action.run();
        });
    }
}
