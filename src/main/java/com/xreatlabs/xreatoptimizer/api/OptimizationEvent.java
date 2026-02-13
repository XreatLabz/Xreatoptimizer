package com.xreatlabs.xreatoptimizer.api;

/**
 * Base class for optimization events
 * Events are fired at key points in the optimization lifecycle
 *
 * @since 1.2.0
 */
public abstract class OptimizationEvent {

    private final long timestamp;
    private boolean cancelled = false;

    protected OptimizationEvent() {
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Get the timestamp when this event was created
     * @return Event timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Check if this event is cancellable
     * @return true if event can be cancelled
     */
    public boolean isCancellable() {
        return false;
    }

    /**
     * Check if this event has been cancelled
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Set the cancelled state of this event
     * @param cancelled true to cancel the event
     */
    public void setCancelled(boolean cancelled) {
        if (!isCancellable()) {
            throw new UnsupportedOperationException("This event cannot be cancelled");
        }
        this.cancelled = cancelled;
    }

    /**
     * Event fired before optimization runs
     */
    public static class BeforeOptimizationEvent extends OptimizationEvent {
        private final String profile;
        private final double tps;
        private final double memoryPercent;

        public BeforeOptimizationEvent(String profile, double tps, double memoryPercent) {
            this.profile = profile;
            this.tps = tps;
            this.memoryPercent = memoryPercent;
        }

        @Override
        public boolean isCancellable() {
            return true;
        }

        public String getProfile() { return profile; }
        public double getTps() { return tps; }
        public double getMemoryPercent() { return memoryPercent; }
    }

    /**
     * Event fired after optimization completes
     */
    public static class AfterOptimizationEvent extends OptimizationEvent {
        private final String profile;
        private final long executionTimeMs;
        private final boolean success;

        public AfterOptimizationEvent(String profile, long executionTimeMs, boolean success) {
            this.profile = profile;
            this.executionTimeMs = executionTimeMs;
            this.success = success;
        }

        public String getProfile() { return profile; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public boolean isSuccess() { return success; }
    }

    /**
     * Event fired when optimization profile changes
     */
    public static class ProfileChangeEvent extends OptimizationEvent {
        private final String oldProfile;
        private final String newProfile;
        private final String reason;

        public ProfileChangeEvent(String oldProfile, String newProfile, String reason) {
            this.oldProfile = oldProfile;
            this.newProfile = newProfile;
            this.reason = reason;
        }

        @Override
        public boolean isCancellable() {
            return true;
        }

        public String getOldProfile() { return oldProfile; }
        public String getNewProfile() { return newProfile; }
        public String getReason() { return reason; }
    }

    /**
     * Event fired when a lag spike is detected
     */
    public static class LagSpikeEvent extends OptimizationEvent {
        private final double peakMs;
        private final String cause;
        private final double tps;

        public LagSpikeEvent(double peakMs, String cause, double tps) {
            this.peakMs = peakMs;
            this.cause = cause;
            this.tps = tps;
        }

        public double getPeakMs() { return peakMs; }
        public String getCause() { return cause; }
        public double getTps() { return tps; }
    }

    /**
     * Event fired when memory pressure is detected
     */
    public static class MemoryPressureEvent extends OptimizationEvent {
        private final double memoryPercent;
        private final long usedMb;
        private final long maxMb;
        private final PressureLevel level;

        public enum PressureLevel {
            LOW, MEDIUM, HIGH, CRITICAL
        }

        public MemoryPressureEvent(double memoryPercent, long usedMb, long maxMb, PressureLevel level) {
            this.memoryPercent = memoryPercent;
            this.usedMb = usedMb;
            this.maxMb = maxMb;
            this.level = level;
        }

        public double getMemoryPercent() { return memoryPercent; }
        public long getUsedMb() { return usedMb; }
        public long getMaxMb() { return maxMb; }
        public PressureLevel getLevel() { return level; }
    }

    /**
     * Event fired when an anomaly is detected
     */
    public static class AnomalyDetectedEvent extends OptimizationEvent {
        private final String anomalyType;
        private final String description;
        private final double severity;

        public AnomalyDetectedEvent(String anomalyType, String description, double severity) {
            this.anomalyType = anomalyType;
            this.description = description;
            this.severity = severity;
        }

        public String getAnomalyType() { return anomalyType; }
        public String getDescription() { return description; }
        public double getSeverity() { return severity; }
    }
}
