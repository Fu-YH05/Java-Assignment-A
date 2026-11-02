package com.cst3492.ta.util;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class TaskAwaiter {
    private TaskAwaiter() {}
    public static <T> T await(Future<T> future, BooleanSupplier cancelled)
            throws InterruptedException, ExecutionException {
        try {
            while (true) {
                if (cancelled.getAsBoolean()) throw new CancellationException();
                try { return future.get(100, TimeUnit.MILLISECONDS); }
                catch (TimeoutException ignored) { }
            }
        } catch (InterruptedException | CancellationException ex) {
            future.cancel(true);
            throw ex;
        }
    }
}
