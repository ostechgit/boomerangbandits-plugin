package com.boomerangbandits.api.models;

import lombok.Getter;

/**
 * Response from POST /api/wom/players/update (202 Accepted).
 */
@Getter
public class PlayerUpdateResponse {
    private String status;
    private String code;
    private Data data;

    @Getter
    public static class Data {
        private String memberId;
        private String accountId;
        private String username;
        private String jobId;

    }
}
