package com.boomerangbandits.api.models;

import lombok.Getter;

import java.util.List;

/**
 * Response from POST /api/wom/groups/{groupId}/sync (200 OK).
 */
@Getter
public class GroupSyncResponse {
    private String status;
    private String code;
    private Data data;

    @Getter
    public static class Data {
        private int groupId;
        private String mode;
        private int added;
        private int updated;
        private int unchanged;
        private int deactivated;
        private int invalid;
        private List<SyncError> errors;

    }

    @Getter
    public static class SyncError {
        private int index;
        private String username;
        private String error;

    }
}
