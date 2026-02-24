package com.boomerangbandits;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BoomerangBanditsConfig.GROUP)
public interface BoomerangBanditsConfig extends Config {
    String GROUP = "boomerangbandits";

    // ======================================================================
    // USER VISIBLE SETTINGS
    // ======================================================================

    @ConfigSection(
        name = "General",
        description = "General plugin settings",
        position = 0
    )
    String generalSection = "general";

    @ConfigItem(
        keyName = "menuPriority",
        name = "Menu Priority",
        description = "Position in the sidebar",
        section = generalSection,
        position = 0
    )
    default int menuPriority() { return 5; }

    // ======================================================================
    // SOUND SETTINGS
    // ======================================================================

    @ConfigSection(
        name = "Sounds",
        description = "Sound effect settings",
        position = 1
    )
    String soundsSection = "sounds";

    @ConfigItem(
        keyName = "cofferDepositSound",
        name = "Coffer Deposit Sound",
        description = "Play a sound when a clan member deposits coins into the coffer",
        section = soundsSection,
        position = 0
    )
    default boolean cofferDepositSound() { return true; }

    // ======================================================================
    // EVENT OVERLAY (Phase 6)
    // ======================================================================

    @ConfigSection(
        name = "Event Overlay",
        description = "In-game overlay for active clan events",
        position = 3
    )
    String eventOverlaySection = "eventOverlay";

    @ConfigItem(
        keyName = "showEventOverlay",
        name = "Show Event Overlay",
        description = "Display active event information in-game",
        section = eventOverlaySection,
        position = 0
    )
    default boolean showEventOverlay() { return false; }

    // ======================================================================
    // ACCOUNT SETTINGS
    // ======================================================================

    @ConfigSection(
        name = "Account",
        description = "Account type and grouping settings",
        position = 4
    )
    String accountSection = "account";

    @ConfigItem(
        keyName = "memberCode",
        name = "Member Code",
        description = "Your Boomerang Bandits member code — provided by a clan admin. Required to use the plugin.",
        section = accountSection,
        position = 0
    )
    default String memberCode() { return ""; }

    // ======================================================================
    // ADMIN (Phase 4)
    // ======================================================================
    // Note: Admin rank IDs are now hardcoded in ClanValidator.java
    // No user-configurable settings needed

    // ======================================================================
    // HIDDEN SETTINGS (managed by plugin internals, not user-editable)
    // ======================================================================

    @ConfigItem(
        keyName = "websiteUrl",
        name = "",
        description = "",
        hidden = true
    )
    default String websiteUrl() { return ""; }

    @ConfigItem(
        keyName = "discordUrl",
        name = "",
        description = "",
        hidden = true
    )
    default String discordUrl() { return ""; }

    @ConfigItem(
        keyName = "announcementMessage",
        name = "",
        description = "",
        hidden = true
    )
    default String announcementMessage() { return ""; }

    @ConfigItem(
        keyName = "rollCallActive",
        name = "",
        description = "",
        hidden = true
    )
    default boolean rollCallActive() { return false; }

    @ConfigItem(
        keyName = "activeCompetitions",
        name = "",
        description = "",
        hidden = true
    )
    default String activeCompetitions() { return "[]"; }

    @ConfigItem(
        keyName = "configSyncInterval",
        name = "",
        description = "",
        hidden = true
    )
    default int configSyncInterval() { return 60; }

    @ConfigItem(
        keyName = "maxRetryAttempts",
        name = "",
        description = "",
        hidden = true
    )
    default int maxRetryAttempts() { return 3; }

    @ConfigItem(
        keyName = "dinkConfigUrl",
        name = "",
        description = "",
        hidden = true
    )
    default String dinkConfigUrl() { return ""; }
}
