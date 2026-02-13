package com.xreatlabs.xreatoptimizer.api;

import java.util.Map;

/**
 * Interface for custom metrics collectors
 * Implement this to collect custom metrics that can be exposed via Prometheus or the web dashboard
 *
 * Example implementation:
 * <pre>
 * {@code
 * public class MyMetricsCollector implements MetricsCollector {
 *     @Override
 *     public String getName() {
 *         return "MyPluginMetrics";
 *     }
 *
 *     @Override
 *     public Map<String, Object> collectMetrics() {
 *         Map<String, Object> metrics = new HashMap<>();
 *         metrics.put("custom_value", getMyCustomValue());
 *         metrics.put("custom_count", getMyCustomCount());
 *         return metrics;
 *     }
 *
 *     @Override
 *     public int getCollectionIntervalSeconds() {
 *         return 5; // Collect every 5 seconds
 *     }
 * }
 * }
 * </pre>
 *
 * @since 1.2.0
 */
public interface MetricsCollector {

    /**
     * Get the name of this metrics collector
     * @return Collector name
     */
    String getName();

    /**
     * Get a description of what metrics this collector provides
     * @return Collector description
     */
    default String getDescription() {
        return "Custom metrics collector";
    }

    /**
     * Collect metrics
     * @return Map of metric names to values
     */
    Map<String, Object> collectMetrics();

    /**
     * Get the interval at which metrics should be collected
     * @return Collection interval in seconds
     */
    default int getCollectionIntervalSeconds() {
        return 10;
    }

    /**
     * Check if this collector should run asynchronously
     * @return true if collector is thread-safe and can run async
     */
    default boolean isAsync() {
        return true;
    }

    /**
     * Called when the collector is registered
     */
    default void onRegister() {
        // Override if needed
    }

    /**
     * Called when the collector is unregistered
     */
    default void onUnregister() {
        // Override if needed
    }
}
