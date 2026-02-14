package com.xreatlabs.xreatoptimizer.api;

/** Optimization strategy interface */
public interface OptimizationStrategy {

    String getName();

    String getDescription();

    boolean shouldExecute(OptimizationContext context);

    OptimizationResult execute(OptimizationContext context);

    default int getPriority() {
        return 0;
    }

    default boolean isAsync() {
        return false;
    }

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
