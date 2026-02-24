package com.boomerangbandits.api.models;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * Response wrapper for GET /api/members/me/player-challenges?incomplete=true
 * <p>
 * Response shape: { "success": true, "player_challenges": [ { ... } ] }
 */
@Getter
public class PlayerChallenge {

    private boolean success;

    @SerializedName("player_challenges")
    private List<Challenge> playerChallenges;

    /**
     * Returns the first challenge, or null if none.
     */
    public Challenge getFirst() {
        return (playerChallenges != null && !playerChallenges.isEmpty())
                ? playerChallenges.get(0)
                : null;
    }

    @Getter
    public static class Challenge {

        /**
         * The challenge description text.
         */
        private String challenge;

        /**
         * Current completion streak (days in a row).
         */
        private int streak;

        /**
         * Total challenges completed by this player.
         */
        @SerializedName("number_completed")
        private int numberCompleted;

        /**
         * Total challenges received by this player.
         */
        @SerializedName("number_received")
        private int numberReceived;

        /**
         * Whether the challenge has been completed.
         */
        private boolean completed;

        /**
         * Whether the player has already rerolled today.
         */
        @SerializedName("rerolled_today")
        private boolean rerolledToday;
    }
}
