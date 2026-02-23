package com.boomerangbandits.util;

import com.boomerangbandits.BoomerangBanditsConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages dynamic event filtering.
 * Controls which notifier event types are enabled/disabled
 * based on local config and (optionally) remote config overrides.
 *
 * Notifiers call isEventAllowed(eventType) before sending.
 *
 * Remote config can push a blocked-events list to disable specific event types
 * server-side (e.g., temporarily disable loot tracking during maintenance).
 */
@Slf4j
@Singleton
public class EventFilterManager {

    private final BoomerangBanditsConfig config;

    /**
     * -- GETTER --
     *  Get the current set of remotely blocked event types.
     */
    // Set of event types blocked by remote config
    @Getter
    private volatile Set<String> remoteBlockedTypes = Collections.emptySet();

    @Inject
    public EventFilterManager(BoomerangBanditsConfig config) {
        this.config = config;
    }

    /**
     * Check if a given event type is allowed to be sent.
     *
     * @param eventType the event type string (e.g., "LOOT", "LEVEL", "DEATH")
     * @return true if the event should be processed, false if filtered out
     */
    public boolean isEventAllowed(String eventType) {
        // Check remote block list first
        if (remoteBlockedTypes.contains(eventType)) {
            log.debug("Event type {} blocked by remote config", eventType);
            return false;
        }

        // Check local config toggles
        // Each notifier has its own config toggle (e.g., enableLootNotifier)
        // This method handles the remote override layer on top of that
        return true;
    }

    /**
     * Update the remote block list.
     * Called by ConfigSyncService when remote config is refreshed.
     *
     * @param blockedTypes set of event type strings to block
     */
    public void updateRemoteBlockList(Set<String> blockedTypes) {
        this.remoteBlockedTypes = blockedTypes != null
            ? Set.copyOf(blockedTypes)
            : Collections.emptySet();
        log.debug("Updated remote block list: {}", remoteBlockedTypes);
    }

}
