package com.boomerangbandits.api.models;

import lombok.Getter;

/**
 * Group member from WiseOldMan API (via backend proxy).
 * Represents a member of the clan with their role and stats.
 *
 * Backend API: GET /api/wom/members
 * 
 * Note: Backend uses UUIDs for player IDs (strings), not integers.
 */
@Getter
public class WomGroupMember {
    private String playerId;
    private String groupId;
    private String role;
    private String createdAt;
    private String updatedAt;
    private String ownerTag;
    private Player player;

    @Getter
    public static class Player {
        private String id;  // UUID string
        private String username;
        private String displayName;
        private String type;
        private String build;
        private String status;
        private long exp;
        private double ehp;
        private double ehb;
        private String registeredAt;
        private String updatedAt;

    }

}
