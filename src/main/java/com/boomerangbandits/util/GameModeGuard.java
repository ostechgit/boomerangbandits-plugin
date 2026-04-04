package com.boomerangbandits.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import net.runelite.client.config.RuneScapeProfileType;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class GameModeGuard {

    @Inject
    private Client client;

    /**
     * Returns true only if the player is on a standard OSRS world.
     * Blocks: Leagues (SEASONAL), Deadman Mode (DEADMAN), Fresh Start (FRESH_START_WORLD),
     * Beta worlds (BETA_WORLD), PvP Arena, Quest Speedrunning, and all other non-STANDARD profiles.
     * Also explicitly blocks Tournament worlds (WorldType.TOURNAMENT_WORLD) which incorrectly
     * return RuneScapeProfileType.STANDARD.
     */
    public boolean isStandardWorld() {
        if (client.getWorldType().isEmpty()) {
            log.debug("[GameModeGuard] Empty world types detected");
            return false;
        }

        RuneScapeProfileType profile = RuneScapeProfileType.getCurrent(client);
        if (profile != RuneScapeProfileType.STANDARD) {
            log.debug("[GameModeGuard] Non-standard profile detected: {}, worldTypes: {}",
                    profile, client.getWorldType());
            return false;
        }
        if (client.getWorldType().contains(WorldType.TOURNAMENT_WORLD)) {
            log.debug("[GameModeGuard] Tournament world detected, worldTypes: {}",
                    client.getWorldType());
            return false;
        }
        return true;
    }
}
