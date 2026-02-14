package com.xreatlabs.xreatoptimizer.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Lag spike event */
public class LagSpikeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final double currentTPS;
    private final double averageTPS;
    private final double dropAmount;
    private final long duration;

    public LagSpikeEvent(double currentTPS, double averageTPS, double dropAmount, long duration) {
        this.currentTPS = currentTPS;
        this.averageTPS = averageTPS;
        this.dropAmount = dropAmount;
        this.duration = duration;
    }

    public double getCurrentTPS() {
        return currentTPS;
    }

    public double getAverageTPS() {
        return averageTPS;
    }

    public double getDropAmount() {
        return dropAmount;
    }

    public long getDuration() {
        return duration;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
