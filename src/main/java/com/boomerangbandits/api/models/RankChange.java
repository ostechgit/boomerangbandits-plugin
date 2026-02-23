package com.boomerangbandits.api.models;

import lombok.Getter;
import lombok.Setter;

/**
 * A pending rank change from the backend.
 * Backend: GET /api/admin/rank-changes/pending
 */
@Setter
@Getter
public class RankChange {
    private String id;
    private String memberRsn;
    private String oldRank;
    private String newRank;
    private String reason;
    private String requestedBy;
    private String requestedAt;     // ISO 8601

}
