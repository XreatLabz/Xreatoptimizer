package com.xreatlabs.xreatoptimizer.api;

import com.xreatlabs.xreatoptimizer.XreatOptimizer;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Public API */
public class XreatOptimizerAPI {

    private static XreatOptimizer plugin;
    private static final Map<Plugin, List<OptimizationStrategy>> strategies = new ConcurrentHashMap<>();
    private static final Map<Plugin, List<MetricsCollector>> collectors = new ConcurrentHashMap<>();
    private static final Map<Plugin, List<OptimizationEventListener>> listeners = new ConcurrentHashMap<>();

    public static void initialize(XreatOptimizer plugin) {
        XreatOptimizerAPI.plugin = plugin;
    }

    public static XreatOptimizer getPlugin() {
        return plugin;
    }

    public static boolean registerStrategy(Plugin owner, OptimizationStrategy strategy) {
        if (owner == null || strategy == null) {
            return false;
        }

        strategies.computeIfAbsent(owner, k -> new ArrayList<>()).add(strategy);
        plugin.getLogger().info("Registered optimization strategy: " + strategy.getName() +
            " from " + owner.getName());
        return true;
    }

    public static void unregisterStrategies(Plugin owner) {
        List<OptimizationStrategy> removed = strategies.remove(owner);
        if (removed != null) {
            plugin.getLogger().info("Unregistered " + removed.size() +
                " strategies from " + owner.getName());
        }
    }

    public static List<OptimizationStrategy> getStrategies() {
        List<OptimizationStrategy> allStrategies = new ArrayList<>();
        strategies.values().forEach(allStrategies::addAll);
        return allStrategies;
    }

    public static boolean registerMetricsCollector(Plugin owner, MetricsCollector collector) {
        if (owner == null || collector == null) {
            return false;
        }

        collectors.computeIfAbsent(owner, k -> new ArrayList<>()).add(collector);
        plugin.getLogger().info("Registered metrics collector: " + collector.getName() +
            " from " + owner.getName());
        return true;
    }

    public static void unregisterMetricsCollectors(Plugin owner) {
        List<MetricsCollector> removed = collectors.remove(owner);
        if (removed != null) {
            plugin.getLogger().info("Unregistered " + removed.size() +
                " metrics collectors from " + owner.getName());
        }
    }

    public static List<MetricsCollector> getMetricsCollectors() {
        List<MetricsCollector> allCollectors = new ArrayList<>();
        collectors.values().forEach(allCollectors::addAll);
        return allCollectors;
    }

    public static boolean registerEventListener(Plugin owner, OptimizationEventListener listener) {
        if (owner == null || listener == null) {
            return false;
        }

        listeners.computeIfAbsent(owner, k -> new ArrayList<>()).add(listener);
        plugin.getLogger().info("Registered event listener from " + owner.getName());
        return true;
    }

    public static void unregisterEventListeners(Plugin owner) {
        List<OptimizationEventListener> removed = listeners.remove(owner);
        if (removed != null) {
            plugin.getLogger().info("Unregistered " + removed.size() +
                " event listeners from " + owner.getName());
        }
    }

    public static List<OptimizationEventListener> getEventListeners() {
        List<OptimizationEventListener> allListeners = new ArrayList<>();
        listeners.values().forEach(allListeners::addAll);
        return allListeners;
    }

    public static void fireEvent(OptimizationEvent event) {
        for (OptimizationEventListener listener : getEventListeners()) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                plugin.getLogger().warning("Error firing event to listener: " + e.getMessage());
            }
        }
    }

    public static void unregisterAll(Plugin owner) {
        unregisterStrategies(owner);
        unregisterMetricsCollectors(owner);
        unregisterEventListeners(owner);
    }

    public static String getAPIVersion() {
        return "1.2.0";
    }
}
