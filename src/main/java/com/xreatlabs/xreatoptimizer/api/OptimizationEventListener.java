package com.xreatlabs.xreatoptimizer.api;

/**
 * Interface for listening to optimization events
 * Implement this to receive notifications about optimization lifecycle events
 *
 * Example implementation:
 * <pre>
 * {@code
 * public class MyEventListener implements OptimizationEventListener {
 *     @Override
 *     public void onEvent(OptimizationEvent event) {
 *         if (event instanceof OptimizationEvent.LagSpikeEvent) {
 *             OptimizationEvent.LagSpikeEvent lagSpike = (OptimizationEvent.LagSpikeEvent) event;
 *             // Handle lag spike
 *             myPlugin.getLogger().warning("Lag spike detected: " + lagSpike.getPeakMs() + "ms");
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * @since 1.2.0
 */
public interface OptimizationEventListener {

    /**
     * Called when an optimization event occurs
     * @param event The event that occurred
     */
    void onEvent(OptimizationEvent event);

    /**
     * Get the priority of this listener (higher = called first)
     * Default priority is 0
     * @return Listener priority
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this listener should receive events asynchronously
     * @return true if listener is thread-safe and can handle async events
     */
    default boolean isAsync() {
        return false;
    }
}
