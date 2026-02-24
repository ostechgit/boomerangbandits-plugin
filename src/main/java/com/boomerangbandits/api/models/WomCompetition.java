package com.boomerangbandits.api.models;

import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Maps to WiseOldMan competition response.
 * Used by WomApiService for deserialisation.
 * <p>
 * WOM API: GET /groups/{id}/competitions
 * WOM API: GET /competitions/{id}
 * <p>
 * Note: WOM API v2 does not include a "status" field.
 * Status is calculated from startsAt/endsAt timestamps.
 */
@Getter
public class WomCompetition {
    // Getters
    private int id;
    private String title;
    private String metric;        // e.g. "woodcutting", "zulrah"
    private String type;          // "classic" or "team"
    private String startsAt;      // ISO 8601
    private String endsAt;        // ISO 8601
    private int groupId;
    private int participantCount;
    private List<WomParticipant> participations;

    /**
     * Calculate competition status based on current time.
     *
     * @return "upcoming", "ongoing", or "finished"
     */
    public String getStatus() {
        if (startsAt == null || endsAt == null) {
            return "unknown";
        }

        try {
            Instant now = Instant.now();
            Instant start = Instant.parse(startsAt);
            Instant end = Instant.parse(endsAt);

            if (now.isBefore(start)) {
                return "upcoming";
            } else if (now.isAfter(end)) {
                return "finished";
            } else {
                return "ongoing";
            }
        } catch (Exception e) {
            return "unknown";
        }
    }

    public boolean isOngoing() {
        return "ongoing".equals(getStatus());
    }

    public boolean isUpcoming() {
        return "upcoming".equals(getStatus());
    }

    public boolean isFinished() {
        return "finished".equals(getStatus());
    }
}
