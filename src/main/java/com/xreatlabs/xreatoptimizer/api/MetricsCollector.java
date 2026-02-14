package com.xreatlabs.xreatoptimizer.api;

import java.util.Map;

/** Metrics collector interface */
public interface MetricsCollector {

    String getName();

    default String getDescription() {
        return "Custom metrics collector";
    }

    Map<String, Object> collectMetrics();

    default int getCollectionIntervalSeconds() {
        return 10;
    }

    default boolean isAsync() {
        return true;
    }

    default void onRegister() {}

    default void onUnregister() {}
}
