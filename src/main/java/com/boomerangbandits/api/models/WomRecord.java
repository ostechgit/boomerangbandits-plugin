package com.boomerangbandits.api.models;

import lombok.Getter;

/**
 * Clan record from WiseOldMan API (via backend proxy).
 * Represents the highest gain ever recorded for a metric/period.
 *
 * Backend API: GET /api/wom/records?metric={metric}&period={period}
 * 
 * Note: Backend uses UUIDs for player IDs (strings), not integers.
 */
@Getter
public class WomRecord {
    private String playerId;  // UUID string
    private String period;
    private String metric;
    private long value;
    private String updatedAt;
    private Player player;

    @Getter
    public static class Player {
        private String id;  // UUID string
        private String username;
        private String displayName;
        private String type;
        private long exp;

    }

}
