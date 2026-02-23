package com.boomerangbandits.api.models;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Response from attendance ingestion endpoint.
 * Backend: POST /api/admin/attendance/ingest
 */
public class AttendanceResult {
    @Setter
    @Getter
    private boolean success;
    @Setter
    @Getter
    private int totalSubmitted;     // entries sent in the payload
    @Setter
    @Getter
    private int matched;            // RSNs matched to known members
    @Setter
    @Getter
    private int pointsAwarded;      // Points per attendee (backend-controlled)
    private List<String> matched_members; // RSNs that were matched
    @Setter
    @Getter
    private List<String> unmatched; // RSNs that couldn't be matched

    public List<String> getMatchedMembers() { return matched_members; }
    public void setMatchedMembers(List<String> matched_members) { this.matched_members = matched_members; }

}
