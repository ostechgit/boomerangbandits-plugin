package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * Response model for GET /api/stats/clan/daily-xp
 * Returns today's clan XP summary: top players, top skills, and active gainer count.
 */
public class DailyXpResponse {

    @Getter
    private boolean success;

    @SerializedName("fromDate")
    private String fromDate;

    @SerializedName("toDate")
    private String toDate;

    @Getter
    @SerializedName("topPlayers")
    private List<TopPlayer> topPlayers;

    @Getter
    @SerializedName("topSkills")
    private List<TopSkill> topSkills;

    @Getter
    @SerializedName("playersWithXpGainToday")
    private int playersWithXpGainToday;

    @Getter
    @SerializedName("asOf")
    private String asOf;

    @Getter
    @SerializedName("isStale")
    private boolean isStale;

    @SerializedName("dataConfidence")
    private String dataConfidence;

    @SerializedName("source")
    private String source;

    public static class TopPlayer {
        @Getter
        private int rank;
        private String memberId;
        @Getter
        private String rsn;
        @Getter
        private long gained;

    }

    @Getter
    public static class TopSkill {
        private int rank;
        private String metric;
        private long gained;

    }
}
