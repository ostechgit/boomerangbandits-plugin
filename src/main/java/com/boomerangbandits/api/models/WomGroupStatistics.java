package com.boomerangbandits.api.models;

import lombok.Getter;

import java.util.List;

/**
 * Group daily gains from WiseOldMan API.
 * Used to display daily clan activity on the Home panel.
 *
 * WOM API: GET /groups/{id}/gained?period=day&metric=overall
 */
@Getter
public class WomGroupStatistics {
    private List<PlayerGain> gains;
    private long totalGained;
    private int activeMembers;

    public static class PlayerGain {
        @Getter
        private Player player;
        private String startDate;
        private String endDate;
        @Getter
        private GainData data;

    }

    @Getter
    public static class Player {
        private String id;  // UUID string
        private String username;
        private String displayName;

    }

    public static class GainData {
        @Getter
        private long gained;
        private long start;
        private long end;

    }

    public void setGains(List<PlayerGain> gains) { this.gains = gains; }
    public void setTotalGained(long totalGained) { this.totalGained = totalGained; }
    public void setActiveMembers(int activeMembers) { this.activeMembers = activeMembers; }
}
