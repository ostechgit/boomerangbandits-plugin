package com.boomerangbandits.api.models;

import lombok.Data;

/**
 * A clan member's profile as returned by the backend.
 * Used in auth response and GET /api/members/me.
 */
@Data
public class MemberProfile {
    private String id;
    private String rsn;
    private String clanRank;
    private int totalPoints;
    private String joinedAt;
}
