package com.boomerangbandits.api.models;

import lombok.Data;

/**
 * Response from POST /api/bounty/complete
 */
@Data
public class BountyCompletionResponse {
    private boolean success;
    private String message;
}
