package com.xreatlabs.xreatoptimizer.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Memory pressure event */
public class MemoryPressureEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final double memoryPercent;
    private final long usedMemoryMB;
    private final long maxMemoryMB;
    private final PressureLevel pressureLevel;

    public enum PressureLevel {
        LOW,      // < 70%
        MEDIUM,   // 70-85%
        HIGH,     // 85-95%
        CRITICAL  // > 95%
    }

    public MemoryPressureEvent(double memoryPercent, long usedMemoryMB, long maxMemoryMB) {
        this.memoryPercent = memoryPercent;
        this.usedMemoryMB = usedMemoryMB;
        this.maxMemoryMB = maxMemoryMB;
        this.pressureLevel = calculatePressureLevel(memoryPercent);
    }

    private PressureLevel calculatePressureLevel(double percent) {
        if (percent >= 95) return PressureLevel.CRITICAL;
        if (percent >= 85) return PressureLevel.HIGH;
        if (percent >= 70) return PressureLevel.MEDIUM;
        return PressureLevel.LOW;
    }

    public double getMemoryPercent() {
        return memoryPercent;
    }

    public long getUsedMemoryMB() {
        return usedMemoryMB;
    }

    public long getMaxMemoryMB() {
        return maxMemoryMB;
    }

    public PressureLevel getPressureLevel() {
        return pressureLevel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
