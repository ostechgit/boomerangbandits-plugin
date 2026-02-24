package com.boomerangbandits.util;

import com.boomerangbandits.BoomerangBanditsConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.clan.*;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates that the local player is a member of the Boomerang Bandits clan.
 * <p>
 * Behaviour: If validation fails, the plugin silently disables features.
 * No error UI is shown — the plugin simply does nothing for non-clan members.
 * <p>
 * Throttling: After initial failure, retries every 5 seconds for 1 minute, then gives up.
 */
@Slf4j
@Singleton
public class ClanValidator {

    /**
     * The in-game clan name. Case-insensitive match.
     */
    private static final String CLAN_NAME = "boomerangrs";

    /**
     * Minimum clan rank required. 0 = any member (including guests with rank).
     * Uses ClanRank integer values, not ordinals.
     */
    private static final int MINIMUM_CLAN_RANK = 0;

    /**
     * Clan title names (lowercase) that grant admin access to the plugin.
     * Matched against the custom title returned by ClanSettings.titleForRank().
     * Add or remove titles here as the clan's rank structure changes.
     */
    private static final String[] ADMIN_TITLE_NAMES = {
            "owner",
            "deputy owner",
            "moderator",
            "sheriff"
    };

    /**
     * Retry interval in milliseconds (5 seconds).
     */
    private static final long RETRY_INTERVAL_MS = 5000;

    /**
     * Maximum retry duration in milliseconds (1 minute).
     */
    private static final long MAX_RETRY_DURATION_MS = 60000;

    @Inject
    private Client client;

    @Inject
    private BoomerangBanditsConfig config;

    private Boolean cachedValidationResult = null;
    private long lastValidationAttempt = 0;
    private long firstFailureTime = 0;
    private boolean hasGivenUp = false;

    /**
     * Validate that the local player is in the Boomerang Bandits clan
     * with sufficient rank.
     * <p>
     * Throttled: After initial failure, retries every 5 seconds for 1 minute, then gives up.
     *
     * @return true if the player is a valid clan member, false otherwise
     */
    public boolean validate() {
        long now = System.currentTimeMillis();

        // If we've already validated successfully, return cached result
        if (cachedValidationResult != null && cachedValidationResult) {
            return true;
        }

        // If we've given up trying, return false immediately
        if (hasGivenUp) {
            return false;
        }

        // Throttle validation attempts - only check every 5 seconds
        if (now - lastValidationAttempt < RETRY_INTERVAL_MS) {
            return cachedValidationResult != null ? cachedValidationResult : false;
        }

        lastValidationAttempt = now;

        // Perform actual validation
        boolean result = performValidation();

        if (result) {
            // Success! Cache it and reset failure tracking
            cachedValidationResult = true;
            firstFailureTime = 0;
            hasGivenUp = false;
            log.info("Clan validation successful");
            return true;
        }

        // Validation failed
        if (firstFailureTime == 0) {
            firstFailureTime = now;
            log.debug("Clan validation failed - will retry every 5s for 1 minute");
        } else if (now - firstFailureTime > MAX_RETRY_DURATION_MS) {
            // We've been trying for over a minute, give up
            hasGivenUp = true;
            log.warn("Clan validation failed after 1 minute of retries - giving up");
        }

        cachedValidationResult = false;
        return false;
    }

    /**
     * Reset validation state (call on logout or clan change).
     */
    public void reset() {
        cachedValidationResult = null;
        lastValidationAttempt = 0;
        firstFailureTime = 0;
        hasGivenUp = false;
    }

    /**
     * Perform the actual validation logic.
     */
    private boolean performValidation() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return false;
        }

        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel == null) {
            log.debug("Not in a clan channel");
            return false;
        }

        String currentClanName = clanChannel.getName();
        if (currentClanName == null || !currentClanName.equalsIgnoreCase(CLAN_NAME)) {
            log.debug("In wrong clan: {} (expected {})", currentClanName, CLAN_NAME);
            return false;
        }

        if (client.getLocalPlayer() == null) {
            return false;
        }

        String playerName = client.getLocalPlayer().getName();
        ClanChannelMember member = clanChannel.findMember(playerName);
        if (member == null) {
            log.debug("Could not find self in clan channel");
            return false;
        }

        ClanRank rank = member.getRank();
        if (rank == null || rank.getRank() < MINIMUM_CLAN_RANK) {
            log.debug("Insufficient clan rank: {}", rank);
            return false;
        }

        return true;
    }

    /**
     * Get the current player's clan rank.
     *
     * @return ClanRank or null if not in the clan
     */
    public ClanRank getCurrentRank() {
        ClanChannel clanChannel = client.getClanChannel();
        if (clanChannel == null || client.getLocalPlayer() == null) {
            log.debug("[ClanValidator] getCurrentRank() - No clan channel or local player");
            return null;
        }

        String playerName = client.getLocalPlayer().getName();
        ClanChannelMember member = clanChannel.findMember(playerName);

        if (member == null) {
            log.debug("[ClanValidator] getCurrentRank() - Player '{}' not found in clan channel", playerName);
            return null;
        }

        ClanRank rank = member.getRank();
        log.trace("[ClanValidator] getCurrentRank() for '{}': rank={}, rankValue={}",
                playerName, rank, rank != null ? rank.getRank() : "null");

        return rank;
    }

    /**
     * Check if the player has admin-level rank.
     * Purely title-name based — matches against ADMIN_TITLE_NAMES using
     * the custom title from ClanSettings. No numeric rank ID checks.
     *
     * @return true if the player's clan title is in the admin list
     */
    public boolean isAdmin() {
        ClanRank rank = getCurrentRank();
        if (rank == null) {
            log.debug("[ClanValidator] isAdmin() - rank is null (not in clan channel yet)");
            return false;
        }

        ClanSettings clanSettings = client.getClanSettings();
        if (clanSettings == null) {
            log.debug("[ClanValidator] isAdmin() - ClanSettings not loaded yet");
            return false;
        }

        ClanTitle title = clanSettings.titleForRank(rank);
        if (title == null || title.getName() == null) {
            log.debug("[ClanValidator] isAdmin() - no title for rank {}", rank.getRank());
            return false;
        }

        String titleName = title.getName().toLowerCase().trim();
        for (String adminTitle : ADMIN_TITLE_NAMES) {
            if (titleName.equals(adminTitle)) {
                log.debug("[ClanValidator] isAdmin() - matched admin title '{}'", titleName);
                return true;
            }
        }

        log.debug("[ClanValidator] isAdmin() - title '{}' not in admin list", titleName);
        return false;
    }
}
