package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
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
    private String clanName;
    private int minimumClanRank;
    private String announcementMessage;
    private boolean rollCallActive;
    private String activeRollCallId;
    private boolean sotwActive;
    private boolean botmActive;
    private boolean teamEventActive;
    private String websiteUrl;

    /** Backend field name is discordInviteUrl */
    @SerializedName("discordInviteUrl")
    private String discordUrl;

    /**
     * If set, written to Dink's "Dynamic Config URL" setting (key: dynamicConfigUrl, group: dinkplugin).
     * Dink fetches this URL on startup and every 3 hours to pull webhook configuration centrally.
     */
    private String dinkConfigUrl;

    /** Outer wrapper matching the actual API response shape: {"success": true, "config": {...}} */
    @Data
    public static class Wrapper {
        private boolean success;
        private PluginConfigResponse config;
    }
}
