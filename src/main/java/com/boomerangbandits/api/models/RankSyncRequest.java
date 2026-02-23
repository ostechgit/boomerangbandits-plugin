package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * Request model for syncing clan ranks to backend.
 */
@Data
public class RankSyncRequest {
    @SerializedName("updates")
    private List<RankUpdate> updates;

    /**
     * When true, the backend will create new member records for any RSN
     * in the updates list that doesn't already exist in the database.
     * New members are created with clan_rank set from the sync payload.
     */
    @SerializedName("createIfNotFound")
    private boolean createIfNotFound;

    @Data
    public static class RankUpdate {
        @SerializedName("rsn")
        private String rsn;

        @SerializedName("clanRank")
        private String clanRank;

        public RankUpdate(String rsn, String clanRank) {
            this.rsn = rsn;
            this.clanRank = clanRank;
        }
    }
}
