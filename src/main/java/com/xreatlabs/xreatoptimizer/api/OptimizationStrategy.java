package com.xreatlabs.xreatoptimizer.api;

/**
 * Interface for custom optimization strategies
 * Implement this to create custom optimization logic that can be executed by XreatOptimizer
 *
 * Example implementation:
 * <pre>
 * {@code
 * public class MyCustomStrategy implements OptimizationStrategy {
 *     @Override
 *     public String getName() {
 *         return "MyCustomOptimization";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Optimizes custom plugin features";
 *     }
 *
 *     @Override
 *     public boolean shouldExecute(OptimizationContext context) {
 *         return context.getTps() < 18.0;
 *     }
 *
 *     @Override
 *     public OptimizationResult execute(OptimizationContext context) {
 *         // Your optimization logic here
 *         return OptimizationResult.success("Optimized successfully");
 *     }
 * }
 * }
 * </pre>
 *
 * @since 1.2.0
 */
public interface OptimizationStrategy {

    /**
     * Get the name of this optimization strategy
     * @return Strategy name
     */
    String getName();

    /**
     * Get a description of what this strategy does
     * @return Strategy description
     */
    String getDescription();

    /**
     * Check if this strategy should execute based on current conditions
     * @param context Current optimization context
     * @return true if strategy should execute
     */
    boolean shouldExecute(OptimizationContext context);

    /**
     * Execute the optimization strategy
     * @param context Current optimization context
     * @return Result of the optimization
     */
    OptimizationResult execute(OptimizationContext context);

    /**
     * Get the priority of this strategy (higher = executed first)
     * Default priority is 0
     * @return Strategy priority
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this strategy can run asynchronously
     * @return true if strategy is thread-safe and can run async
     */
    default boolean isAsync() {
        return false;
    }

    /**
     * Optimization context provided to strategies
     */
    class OptimizationContext {
        private final double tps;
        private final double memoryPercent;
        private final int entityCount;
        private final int chunkCount;
        private final int playerCount;
        private final String currentProfile;

        public OptimizationContext(double tps, double memoryPercent, int entityCount,
                                   int chunkCount, int playerCount, String currentProfile) {
            this.tps = tps;
            this.memoryPercent = memoryPercent;
            this.entityCount = entityCount;
            this.chunkCount = chunkCount;
            this.playerCount = playerCount;
            this.currentProfile = currentProfile;
        }

        public double getTps() { return tps; }
        public double getMemoryPercent() { return memoryPercent; }
        public int getEntityCount() { return entityCount; }
        public int getChunkCount() { return chunkCount; }
        public int getPlayerCount() { return playerCount; }
        public String getCurrentProfile() { return currentProfile; }
    }

    /**
     * Result of an optimization execution
     */
    class OptimizationResult {
        private final boolean success;
        private final String message;
        private final long executionTimeMs;

        private OptimizationResult(boolean success, String message, long executionTimeMs) {
            this.success = success;
            this.message = message;
            this.executionTimeMs = executionTimeMs;
        }

        public static OptimizationResult success(String message) {
            return new OptimizationResult(true, message, 0);
        }

        public static OptimizationResult failure(String message) {
            return new OptimizationResult(false, message, 0);
        }

        public static OptimizationResult success(String message, long executionTimeMs) {
            return new OptimizationResult(true, message, executionTimeMs);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public long getExecutionTimeMs() { return executionTimeMs; }
    }
}
