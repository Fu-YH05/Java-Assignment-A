package com.cst3492.ta.util;

import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.concurrency.AppExecutorUtil;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** A cancellable IDE task whose network worker is interrupted on cancellation or project disposal. */
public final class BackgroundWork {
    private BackgroundWork() {}
    @FunctionalInterface public interface Work<T> { T call(ProgressIndicator indicator) throws Exception; }
    public static final class Handle {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Future<?> future;
        private volatile ProgressIndicator indicator;
        public void cancel() {
            cancelled.set(true);
            if (indicator != null) indicator.cancel();
            if (future != null) future.cancel(true);
        }
        public boolean isCancelled() { return cancelled.get() || indicator != null && indicator.isCanceled(); }
    }
    public static <T> Handle run(Project project, String title, Work<T> work, Consumer<T> success) {
        return run(project, title, work, success,
                error -> Messages.showErrorDialog(project, error, title), () -> {});
    }
    public static <T> Handle run(Project project, String title, Work<T> work, Consumer<T> success,
                                 Consumer<String> failure, Runnable finished) {
        Handle handle = new Handle();
        new Task.Backgroundable(project, title, true) {
            @Override public void run(ProgressIndicator indicator) {
                handle.indicator = indicator;
                indicator.setIndeterminate(true);
                try {
                    if (handle.isCancelled()) throw new CancellationException();
                    Future<T> future = AppExecutorUtil.getAppExecutorService().submit(() -> {
                        indicator.checkCanceled();
                        T result = work.call(indicator);
                        indicator.checkCanceled();
                        return result;
                    });
                    handle.future = future;
                    if (handle.isCancelled()) future.cancel(true);
                    T result = TaskAwaiter.await(future,
                            () -> project.isDisposed() || handle.isCancelled() || indicator.isCanceled());
                    T value = result;
                    SwingUtilities.invokeLater(() -> {
                        if (!project.isDisposed() && !handle.isCancelled()) success.accept(value);
                    });
                } catch (CancellationException ex) {
                    handle.cancel();
                } catch (InterruptedException ex) {
                    handle.cancel();
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException ? ex.getCause() : ex;
                    if (cause instanceof CancellationException || cause instanceof ProcessCanceledException
                            || cause instanceof InterruptedException || indicator.isCanceled()) {
                        handle.cancel();
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            if (!project.isDisposed() && !handle.isCancelled()) failure.accept(
                                    cause.getMessage() == null ? cause.toString() : cause.getMessage());
                        });
                    }
                } finally {
                    SwingUtilities.invokeLater(() -> { if (!project.isDisposed()) finished.run(); });
                }
            }
        }.queue();
        return handle;
    }
}
