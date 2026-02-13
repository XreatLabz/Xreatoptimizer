package com.xreatlabs.xreatoptimizer.api;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main API class for XreatOptimizer extensions
 * Allows third-party plugins to extend XreatOptimizer functionality
 *
 * Example usage:
 * <pre>
 * {@code
 * // Register a custom optimization strategy
 * XreatOptimizerAPI.registerStrategy(myPlugin, new MyCustomStrategy());
 *
 * // Register a custom metrics collector
 * XreatOptimizerAPI.registerMetricsCollector(myPlugin, new MyMetricsCollector());
 *
 * // Listen to optimization events
 * XreatOptimizerAPI.registerEventListener(myPlugin, new MyEventListener());
 * }
 * </pre>
 *
 * @since 1.2.0
 */
public class XreatOptimizerAPI {

    private static XreatOptimizer plugin;
    private static final Map<Plugin, List<OptimizationStrategy>> strategies = new ConcurrentHashMap<>();
    private static final Map<Plugin, List<MetricsCollector>> collectors = new ConcurrentHashMap<>();
    private static final Map<Plugin, List<OptimizationEventListener>> listeners = new ConcurrentHashMap<>();

    /**
     * Initialize the API (called by XreatOptimizer on startup)
     * @param plugin The XreatOptimizer plugin instance
     */
    public static void initialize(XreatOptimizer plugin) {
        XreatOptimizerAPI.plugin = plugin;
    }

    /**
     * Get the XreatOptimizer plugin instance
     * @return The plugin instance
     */
    public static XreatOptimizer getPlugin() {
        return plugin;
    }

    /**
     * Register a custom optimization strategy
     * @param owner The plugin registering the strategy
     * @param strategy The optimization strategy to register
     * @return true if registered successfully
     */
    public static boolean registerStrategy(Plugin owner, OptimizationStrategy strategy) {
        if (owner == null || strategy == null) {
            return false;
        }

        strategies.computeIfAbsent(owner, k -> new ArrayList<>()).add(strategy);
        plugin.getLogger().info("Registered optimization strategy: " + strategy.getName() +
            " from " + owner.getName());
        return true;
    }

    /**
     * Unregister all strategies from a plugin
     * @param owner The plugin to unregister strategies from
     */
    public static void unregisterStrategies(Plugin owner) {
        List<OptimizationStrategy> removed = strategies.remove(owner);
        if (removed != null) {
            plugin.getLogger().info("Unregistered " + removed.size() +
                " strategies from " + owner.getName());
        }
    }

    /**
     * Get all registered optimization strategies
     * @return List of all registered strategies
     */
    public static List<OptimizationStrategy> getStrategies() {
        List<OptimizationStrategy> allStrategies = new ArrayList<>();
        strategies.values().forEach(allStrategies::addAll);
        return allStrategies;
    }

    /**
     * Register a custom metrics collector
     * @param owner The plugin registering the collector
     * @param collector The metrics collector to register
     * @return true if registered successfully
     */
    public static boolean registerMetricsCollector(Plugin owner, MetricsCollector collector) {
        if (owner == null || collector == null) {
            return false;
        }

        collectors.computeIfAbsent(owner, k -> new ArrayList<>()).add(collector);
        plugin.getLogger().info("Registered metrics collector: " + collector.getName() +
            " from " + owner.getName());
        return true;
    }

    /**
     * Unregister all metrics collectors from a plugin
     * @param owner The plugin to unregister collectors from
     */
    public static void unregisterMetricsCollectors(Plugin owner) {
        List<MetricsCollector> removed = collectors.remove(owner);
        if (removed != null) {
            plugin.getLogger().info("Unregistered " + removed.size() +
                " metrics collectors from " + owner.getName());
        }
    }

    /**
     * Get all registered metrics collectors
     * @return List of all registered collectors
     */
    public static List<MetricsCollector> getMetricsCollectors() {
        List<MetricsCollector> allCollectors = new ArrayList<>();
        collectors.values().forEach(allCollectors::addAll);
        return allCollectors;
    }

    /**
     * Register an event listener
     * @param owner The plugin registering the listener
     * @param listener The event listener to register
     * @return true if registered successfully
     */
    public static boolean registerEventListener(Plugin owner, OptimizationEventListener listener) {
        if (owner == null || listener == null) {
            return false;
        }

        listeners.computeIfAbsent(owner, k -> new ArrayList<>()).add(listener);
        plugin.getLogger().info("Registered event listener from " + owner.getName());
        return true;
    }

    /**
     * Unregister all event listeners from a plugin
     * @param owner The plugin to unregister listeners from
     */
    public static void unregisterEventListeners(Plugin owner) {
        List<OptimizationEventListener> removed = listeners.remove(owner);
        if (removed != null) {
            plugin.getLogger().info("Unregistered " + removed.size() +
                " event listeners from " + owner.getName());
        }
    }

    /**
     * Get all registered event listeners
     * @return List of all registered listeners
     */
    public static List<OptimizationEventListener> getEventListeners() {
        List<OptimizationEventListener> allListeners = new ArrayList<>();
        listeners.values().forEach(allListeners::addAll);
        return allListeners;
    }

    /**
     * Fire an optimization event to all registered listeners
     * @param event The event to fire
     */
    public static void fireEvent(OptimizationEvent event) {
        for (OptimizationEventListener listener : getEventListeners()) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                plugin.getLogger().warning("Error firing event to listener: " + e.getMessage());
            }
        }
    }

    /**
     * Unregister all extensions from a plugin
     * @param owner The plugin to unregister from
     */
    public static void unregisterAll(Plugin owner) {
        unregisterStrategies(owner);
        unregisterMetricsCollectors(owner);
        unregisterEventListeners(owner);
    }

    /**
     * Get API version
     * @return API version string
     */
    public static String getAPIVersion() {
        return "1.2.0";
    }
}
