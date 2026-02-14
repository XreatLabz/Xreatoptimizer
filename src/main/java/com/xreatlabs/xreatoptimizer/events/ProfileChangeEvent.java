package com.xreatlabs.xreatoptimizer.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Profile change event */
public class ProfileChangeEvent extends Event {
    private static final HandlerList handlers = new HandlerList();
    private final String oldProfile;
    private final String newProfile;
    private final String reason;

    public ProfileChangeEvent(String oldProfile, String newProfile, String reason) {
        this.oldProfile = oldProfile;
        this.newProfile = newProfile;
        this.reason = reason;
    }

    public String getOldProfile() {
        return oldProfile;
    }

    public String getNewProfile() {
        return newProfile;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
