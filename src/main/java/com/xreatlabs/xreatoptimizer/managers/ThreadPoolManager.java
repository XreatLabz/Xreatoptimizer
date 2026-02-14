package com.xreatlabs.xreatoptimizer.managers;

import com.xreatlabs.xreatoptimizer.utils.LoggerUtils;

import java.util.concurrent.*;

public class ThreadPoolManager {
    private final ThreadPoolExecutor chunkTaskPool;
    private final ThreadPoolExecutor entityCleanupPool;
    private final ThreadPoolExecutor analyticsPool;
    private final ThreadPoolExecutor ioPool;
    
    public ThreadPoolManager() {
        int cores = Runtime.getRuntime().availableProcessors();
        
        int chunkPoolSize = Math.min(cores, 4);
        this.chunkTaskPool = new ThreadPoolExecutor(
            Math.max(1, chunkPoolSize / 2),
            chunkPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "XreatOpt-ChunkTask-" + counter++);
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }
            }
        );
        
        int entityPoolSize = Math.max(1, cores / 2);
        this.entityCleanupPool = new ThreadPoolExecutor(
            1,
            entityPoolSize,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "XreatOpt-EntityCleanup-" + counter++);
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }
            }
        );
        
        this.analyticsPool = new ThreadPoolExecutor(
            1,
            2,
            30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(20),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "XreatOpt-Analytics-" + counter++);
                    t.setPriority(Thread.MIN_PRIORITY);
                    return t;
                }
            }
        );
        
        int ioPoolSize = Math.max(1, cores / 2);
        this.ioPool = new ThreadPoolExecutor(
            1,
            ioPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "XreatOpt-IO-" + counter++);
                    t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                }
            }
        );
        
        LoggerUtils.info("Thread pool manager initialized with " + cores + " CPU cores detected");
        LoggerUtils.info("Chunk Pool: " + chunkPoolSize + " threads");
        LoggerUtils.info("Entity Pool: " + entityPoolSize + " threads");
        LoggerUtils.info("Analytics Pool: 2 threads");
        LoggerUtils.info("I/O Pool: " + ioPoolSize + " threads");
    }
    
    public ThreadPoolExecutor getChunkTaskPool() {
        return chunkTaskPool;
    }
    
    public ThreadPoolExecutor getEntityCleanupPool() {
        return entityCleanupPool;
    }
    
    public ThreadPoolExecutor getAnalyticsPool() {
        return analyticsPool;
    }
    
    public ThreadPoolExecutor getIoPool() {
        return ioPool;
    }
    
    public void executeChunkTask(Runnable task) {
        chunkTaskPool.execute(task);
    }
    
    public void executeEntityCleanupTask(Runnable task) {
        entityCleanupPool.execute(task);
    }
    
    public void executeAnalyticsTask(Runnable task) {
        analyticsPool.execute(task);
    }
    
    public void executeIoTask(Runnable task) {
        ioPool.execute(task);
    }
    
    public void shutdown() {
        LoggerUtils.info("Shutting down thread pools...");
        
        shutdownPool(chunkTaskPool, "Chunk Task Pool");
        shutdownPool(entityCleanupPool, "Entity Cleanup Pool");
        shutdownPool(analyticsPool, "Analytics Pool");
        shutdownPool(ioPool, "I/O Pool");
        
        LoggerUtils.info("All thread pools have been shut down.");
    }
    
    private void shutdownPool(ThreadPoolExecutor pool, String name) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                LoggerUtils.warn(name + " did not terminate in time, forcing shutdown...");
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    LoggerUtils.error(name + " could not be terminated!");
                }
            }
        } catch (InterruptedException e) {
            LoggerUtils.error("Interrupted while waiting for " + name + " to terminate", e);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public int getActiveThreadCount() {
        return chunkTaskPool.getActiveCount() +
               entityCleanupPool.getActiveCount() +
               analyticsPool.getActiveCount() +
               ioPool.getActiveCount();
    }

    public int getQueuedTaskCount() {
        return chunkTaskPool.getQueue().size() +
               entityCleanupPool.getQueue().size() +
               analyticsPool.getQueue().size() +
               ioPool.getQueue().size();
    }
}
