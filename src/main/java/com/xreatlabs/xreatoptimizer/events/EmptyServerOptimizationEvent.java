package com.xreatlabs.xreatoptimizer.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Empty server optimization event */
public class EmptyServerOptimizationEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final boolean entering;
    private final int chunksUnloaded;
    private final int entitiesRemoved;
    private final long memorySavedMB;

    public EmptyServerOptimizationEvent(boolean entering, int chunksUnloaded,
                                       int entitiesRemoved, long memorySavedMB) {
        this.entering = entering;
        this.chunksUnloaded = chunksUnloaded;
        this.entitiesRemoved = entitiesRemoved;
        this.memorySavedMB = memorySavedMB;
    }

    public boolean isEntering() {
        return entering;
    }

    public int getChunksUnloaded() {
        return chunksUnloaded;
    }

    public int getEntitiesRemoved() {
        return entitiesRemoved;
    }

    public long getMemorySavedMB() {
        return memorySavedMB;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
