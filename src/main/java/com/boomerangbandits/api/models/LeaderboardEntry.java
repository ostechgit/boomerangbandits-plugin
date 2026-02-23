package com.boomerangbandits.api.models;

import lombok.Getter;
import lombok.Setter;

/**
 * Single row in the clan leaderboard.
 * Backend: GET /api/leaderboard
 */
@Setter
@Getter
public class LeaderboardEntry {
    private int rank;
    private String rsn;
    private String clanRank;
    private int totalPoints;
    private String lastSeen;    // ISO 8601

}
