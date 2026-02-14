package com.xreatlabs.xreatoptimizer.async;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Async execution utility */
public class AsyncExecutor {
    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "XreatOpt-AsyncExecutor");
        t.setDaemon(true);
        return t;
    });
    
    public static CompletableFuture<Void> executeAsync(Runnable task) {
        return CompletableFuture.runAsync(task, executor);
    }
    
    public static <T> CompletableFuture<T> executeAsyncWithResult(java.util.concurrent.Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }
    
    public static CompletableFuture<Void> executeAsyncWithErrorHandler(Runnable task, java.util.function.Consumer<Throwable> errorHandler) {
        return CompletableFuture.runAsync(task, executor)
            .exceptionally(throwable -> {
                errorHandler.accept(throwable);
                return null;
            });
    }
    
    public static void shutdown() {
        executor.shutdown();
    }
}