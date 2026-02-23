package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

import java.util.List;

/**
 * Response model for rank sync operation.
 */
@Data
public class RankSyncResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("synced")
    private int synced;

    @SerializedName("updated")
    private int updated;

    @SerializedName("unchanged")
    private int unchanged;

    @SerializedName("notFound")
    private List<String> notFound;

    @SerializedName("created")
    private int created;

    @SerializedName("invalid")
    private int invalid;

    @SerializedName("errors")
    private List<SyncError> errors;

    /**
     * Individual sync error details.
     */
    @Data
    public static class SyncError {
        @SerializedName("index")
        private Integer index;

        @SerializedName("rsn")
        private String rsn;

        @SerializedName("rsnInput")
        private String rsnInput;

        @SerializedName("error")
        private String error;
    }
}
