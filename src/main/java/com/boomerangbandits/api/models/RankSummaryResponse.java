package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * Response model for rank summary endpoint.
 * Shows distribution of clan members by rank.
 */
@Data
public class RankSummaryResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("totalMembers")
    private int totalMembers;

    @SerializedName("ranks")
    private List<RankCount> ranks;

    @Data
    public static class RankCount {
        @SerializedName("rank")
        private String rank;

        @SerializedName("count")
        private int count;
    }
}
