package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * Response from GET /api/plugin/config
 * <p>
 * Backend returns: {"success": true, "config": { ...fields... }}
 * The outer wrapper is handled by PluginConfigWrapper — this class
 * represents the inner "config" object only.
 */
@Data
public class PluginConfigResponse {
    private boolean rollCallActive;
    private int womListPollingIntervalMinutes = 10;
    private int womDetailPollingIntervalMinutes = 5;
    private String announcementMessage;
    private List<String> announcements;
    private String websiteUrl;

    /**
     * Backend field name is discordInviteUrl
     */
    @SerializedName("discordInviteUrl")
    private String discordUrl;

    /**
     * If set, written to Dink's "Dynamic Config URL" setting (key: dynamicConfigUrl, group: dinkplugin).
     * Dink fetches this URL on startup and every 3 hours to pull webhook configuration centrally.
     */
    private String dinkConfigUrl;

    /**
     * Feature flags: server-controlled kill-switches for optional features.
     * Key: feature name (e.g., "bounties", "itemRenames")
     * Value: true = enabled, false = disabled
     */
    private Map<String, Boolean> features;

    /**
     * Bounty definitions from the manifest.
     */
    private List<Bounty> bounties;

    /**
     * Item name renames (e.g., "Twisted bow" -> "Twisted Bow (Renamed)").
     * Key: original item name, Value: display name
     */
    private Map<String, String> itemRenames;

    /**
     * NPC name renames (e.g., "Zulrah" -> "Zulrah (Renamed)").
     * Key: original NPC name, Value: display name
     */
    private Map<String, String> npcRenames;

    /**
     * Outer wrapper matching the actual API response shape: {"success": true, "config": {...}}
     */
    @Data
    public static class Wrapper {
        private boolean success;
        private PluginConfigResponse config;
    }

    /**
     * Bounty definition from the manifest.
     */
    @Data
    public static class Bounty {
        private String id;
        private String name;
        private String description;
        private List<BountyItem> items;
    }

    /**
     * Item within a bounty.
     */
    @Data
    public static class BountyItem {
        private String name;
        private List<Integer> npcIds;
        private int itemId;
    }
}
