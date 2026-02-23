package com.boomerangbandits.api.models;

import lombok.Getter;

/**
 * A single member's attendance data within an event submission.
 * Sent as part of the structured JSON payload to POST /api/admin/attendance/ingest.
 */
@Getter
public class AttendanceEntry {
    private final String rsn;
    private final int secondsPresent;
    private final int secondsLate;   // 0 if not late
    private final boolean meetsThreshold;

    public AttendanceEntry(String rsn, int secondsPresent, int secondsLate, boolean meetsThreshold) {
        this.rsn = rsn;
        this.secondsPresent = secondsPresent;
        this.secondsLate = secondsLate;
        this.meetsThreshold = meetsThreshold;
    }

}
