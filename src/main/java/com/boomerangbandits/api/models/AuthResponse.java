package com.boomerangbandits.api.models;

import lombok.Data;

/**
 * Response from POST /api/auth/verify
 */
@Data
public class AuthResponse {
    private boolean success;
    private boolean pending;
    private String memberCode;
    private MemberProfile member;
    private String error;

    /** True when the backend registered the member but hasn't issued a code yet. */
    public boolean isPending() {
        return pending;
    }
}
