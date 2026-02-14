package com.xreatlabs.xreatoptimizer.api;

/** Event listener interface */
public interface OptimizationEventListener {

    void onEvent(OptimizationEvent event);

    default int getPriority() {
        return 0;
    }

    default boolean isAsync() {
        return false;
    }
}
