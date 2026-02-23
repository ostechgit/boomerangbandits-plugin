package com.boomerangbandits.api.models;

import lombok.Getter;

/**
 * A single participant in a WOM competition.
 * Nested inside WomCompetition.participations[].
 *
 * WOM API structure:
 * {
 *   "player": { "id": 123, "displayName": "Player", "type": "regular" },
 *   "progress": { "start": 1000, "end": 2000, "gained": 1000 },
 *   "levels": { "start": 50, "end": 55, "gained": 5 }
 * }
 */
@Getter
public class WomParticipant {
    private WomPlayer player;
    private Progress progress;
    private Levels levels;

    // Inner class for nested "player" object
    @Getter
    public static class WomPlayer {
        private String id;  // UUID string
        private String displayName;
        private String type;     // "regular", "ironman", etc.

    }

    // Inner class for nested "progress" object
    @Getter
    public static class Progress {
        private long start;
        private long end;
        private long gained;

    }

    // Inner class for nested "levels" object
    @Getter
    public static class Levels {
        private int start;
        private int end;
        private int gained;

    }

    // Convenience methods
    public long getStartValue() { 
        return progress != null ? progress.getStart() : 0; 
    }
    
    public long getEndValue() { 
        return progress != null ? progress.getEnd() : 0; 
    }
    
    public long getGained() { 
        return progress != null ? progress.getGained() : 0; 
    }
    
    public String getDisplayName() {
        return player != null ? player.getDisplayName() : "Unknown";
    }
}
