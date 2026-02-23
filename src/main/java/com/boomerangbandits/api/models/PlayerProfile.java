package com.boomerangbandits.api.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Full player profile from backend.
 * Backend: GET /api/members/me (with X-Member-Code header)
 */
@Setter
@Getter
public class PlayerProfile {
    private String id;
    private String rsn;
    private String clanRank;
    private String joinDate;        // ISO 8601
    private String lastSeen;        // ISO 8601
    private PointsBreakdown points;
    private List<RecentEvent> recentEvents;

    // Nested recent event
    @Setter
    @Getter
    public static class RecentEvent {
        private String type;
        private String description;
        private int pointsEarned;
        private String timestamp;

    }

}
