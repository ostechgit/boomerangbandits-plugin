package com.boomerangbandits.util;

import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.api.models.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Provides hardcoded test data for development/testing without a running backend.
 * Activated when the hidden config item "devMode" is true.
 *
 * To enable: Open RuneLite config editor in developer mode,
 * find "boomerangbandits" group, set devMode = true.
 */
public class DevModeDataProvider {

    public static AuthResponse getAuthResponse(String rsn) {
        MemberProfile profile = new MemberProfile();
        profile.setId("dev-uuid-1234");
        profile.setRsn(rsn);
        profile.setClanRank("corporal");
        profile.setTotalPoints(150);
        profile.setJoinedAt("2025-01-15T00:00:00Z");

        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setMemberCode("dev-member-code-abc123");
        response.setMember(profile);
        return response;
    }

    public static PluginConfigResponse getPluginConfig() {
        PluginConfigResponse config = new PluginConfigResponse();
        config.setClanName("boomerangrs");
        config.setMinimumClanRank(0);
        config.setAnnouncementMessage("Welcome to Boomerang Bandits! [DEV MODE]");
        config.setRollCallActive(false);
        config.setActiveRollCallId(null);
        config.setSotwActive(true);
        config.setBotmActive(false);
        config.setTeamEventActive(false);
        config.setWebsiteUrl("https://boomerangbandits.com");
        return config;
    }

    public static MemberProfile getMemberProfile(String rsn) {
        MemberProfile profile = new MemberProfile();
        profile.setId("dev-uuid-1234");
        profile.setRsn(rsn);
        profile.setClanRank("corporal");
        profile.setTotalPoints(150);
        profile.setJoinedAt("2025-01-15T00:00:00Z");
        return profile;
    }

    /**
     * Mock event response from POST /api/events
     */
    public static String getEventResponse(String eventType) {
        int points;
        switch (eventType) {
            case "LOOT": points = 5; break;
            case "LEVEL": points = 20; break;
            case "DEATH": points = 0; break;
            case "PET": points = 100; break;
            case "KILL_COUNT": points = 10; break;
            case "QUEST": points = 25; break;
            case "DIARY": points = 50; break;
            case "COMBAT_ACHIEVEMENT": points = 15; break;
            case "COLLECTION": points = 10; break;
            case "CLUE": points = 10; break;
            case "LOGIN": points = 0; break;
            case "LOGOUT": points = 0; break;
            default: points = 5; break;
        }
        return String.format("{\"success\":true,\"event_id\":\"dev-evt-123\",\"points_awarded\":%d}", points);
    }

    // ======================================================================
    // PHASE 3: MOCK DATA FOR NEW ENDPOINTS
    // ======================================================================

    /**
     * Mock player profile for GET /api/members/me
     */
    public static PlayerProfile getPlayerProfile(String rsn) {
        PlayerProfile profile = new PlayerProfile();
        
        // Basic info
        profile.setId("dev-player-uuid-456");
        profile.setRsn(rsn);
        profile.setClanRank("Corporal");
        profile.setJoinDate("2025-01-15T10:30:00Z");
        profile.setLastSeen("2025-02-06T20:00:00Z");
        
        // Points breakdown
        PointsBreakdown points = new PointsBreakdown();
        points.setTotal(1250);
        points.setLifetime(1500);
        points.setLoot(350);
        points.setSkill(420);
        points.setPvm(280);
        points.setEvent(150);
        points.setMisc(50);
        profile.setPoints(points);
        
        // Recent events
        List<PlayerProfile.RecentEvent> events = new ArrayList<>();
        
        PlayerProfile.RecentEvent event1 = new PlayerProfile.RecentEvent();
        event1.setType("LOOT");
        event1.setDescription("Received Abyssal Whip drop");
        event1.setPointsEarned(20);
        event1.setTimestamp("2025-02-06T19:45:00Z");
        events.add(event1);
        
        PlayerProfile.RecentEvent event2 = new PlayerProfile.RecentEvent();
        event2.setType("LEVEL");
        event2.setDescription("Reached level 85 Slayer");
        event2.setPointsEarned(10);
        event2.setTimestamp("2025-02-06T18:30:00Z");
        events.add(event2);
        
        PlayerProfile.RecentEvent event3 = new PlayerProfile.RecentEvent();
        event3.setType("QUEST");
        event3.setDescription("Completed Dragon Slayer II");
        event3.setPointsEarned(25);
        event3.setTimestamp("2025-02-06T16:15:00Z");
        events.add(event3);
        
        PlayerProfile.RecentEvent event4 = new PlayerProfile.RecentEvent();
        event4.setType("KILL_COUNT");
        event4.setDescription("50 Zulrah kills");
        event4.setPointsEarned(15);
        event4.setTimestamp("2025-02-06T14:00:00Z");
        events.add(event4);
        
        PlayerProfile.RecentEvent event5 = new PlayerProfile.RecentEvent();
        event5.setType("COLLECTION");
        event5.setDescription("New collection log item");
        event5.setPointsEarned(10);
        event5.setTimestamp("2025-02-06T12:30:00Z");
        events.add(event5);
        
        profile.setRecentEvents(events);
        
        return profile;
    }

    /**
     * Mock leaderboard for GET /api/leaderboard
     */
    public static ClanApiService.LeaderboardResponse getLeaderboard(int page, int perPage) {
        ClanApiService.LeaderboardResponse response = new ClanApiService.LeaderboardResponse();
        
        List<LeaderboardEntry> entries = new ArrayList<>();
        
        // Generate mock leaderboard entries
        String[] names = {
            "DevPlayer1", "TestUser2", "MockPlayer3", "DemoUser4", "SamplePlayer5",
            "ExampleUser6", "TrialPlayer7", "ProtoUser8", "AlphaPlayer9", "BetaUser10",
            "GammaPlayer11", "DeltaUser12", "EpsilonPlayer13", "ZetaUser14", "EtaPlayer15",
            "ThetaUser16", "IotaPlayer17", "KappaUser18", "LambdaPlayer19", "MuUser20"
        };
        
        String[] ranks = {
            "General", "Captain", "Lieutenant", "Sergeant", "Corporal",
            "Corporal", "Recruit", "Recruit", "Recruit", "Recruit",
            "Recruit", "Recruit", "Recruit", "Recruit", "Recruit",
            "Recruit", "Recruit", "Recruit", "Recruit", "Recruit"
        };
        
        int startRank = (page - 1) * perPage + 1;
        int endRank = Math.min(startRank + perPage - 1, 100); // Mock 100 total members
        
        for (int i = startRank; i <= endRank && i <= names.length; i++) {
            LeaderboardEntry entry = new LeaderboardEntry();
            entry.setRank(i);
            entry.setRsn(names[i - 1]);
            entry.setClanRank(ranks[i - 1]);
            entry.setTotalPoints(2000 - (i * 15));
            entry.setLastSeen("2025-02-06T" + String.format("%02d", 20 - (i % 24)) + ":00:00Z");
            entries.add(entry);
        }
        
        response.setLeaderboard(entries);
        response.setTotal(100); // Mock 100 total members
        response.setPage(page);
        response.setPages((int) Math.ceil(100.0 / perPage));
        
        return response;
    }

    // ======================================================================
    /**
     * Mock active event for GET /api/events/active
     */
    public static ActiveEvent getActiveEvent() {
        ActiveEvent activeEvent = new ActiveEvent();
        activeEvent.setActive(true);
        
        EventDetails event = new EventDetails();
        event.setName("Tuesday Raids [DEV MODE]");
        event.setEventPassword("boomerang123");
        event.setChallengePassword("challenge456");
        event.setLocation("Theatre of Blood");
        event.setWorld(416);
        event.setEndTime("2025-02-16T22:00:00Z");
        
        activeEvent.setEvents(java.util.Collections.singletonList(event));
        return activeEvent;
    }

    // ======================================================================
    // PHASE 4: ADMIN MOCK DATA
    // ======================================================================

    /**
     * Mock rank changes for GET /api/admin/rank-changes/pending
     */
    public static List<RankChange> getTestRankChanges() {
        List<RankChange> changes = new ArrayList<>();
        
        RankChange change1 = new RankChange();
        change1.setId("rc-001");
        change1.setMemberRsn("TestPlayer1");
        change1.setOldRank("Recruit");
        change1.setNewRank("Corporal");
        change1.setReason("Active participation in events");
        change1.setRequestedAt("2025-02-15T10:00:00Z");
        changes.add(change1);
        
        RankChange change2 = new RankChange();
        change2.setId("rc-002");
        change2.setMemberRsn("TestPlayer2");
        change2.setOldRank("Corporal");
        change2.setNewRank("Sergeant");
        change2.setReason("Leadership during raids");
        change2.setRequestedAt("2025-02-14T15:30:00Z");
        changes.add(change2);
        
        RankChange change3 = new RankChange();
        change3.setId("rc-003");
        change3.setMemberRsn("TestPlayer3");
        change3.setOldRank("Sergeant");
        change3.setNewRank("Lieutenant");
        change3.setReason("Consistent event hosting");
        change3.setRequestedAt("2025-02-13T20:00:00Z");
        changes.add(change3);
        
        return changes;
    }

    /**
     * Mock attendance result for POST /api/admin/attendance/ingest
     */
    public static AttendanceResult getTestAttendanceResult() {
        AttendanceResult result = new AttendanceResult();
        result.setSuccess(true);
        result.setTotalSubmitted(15);
        result.setMatched(12);
        result.setPointsAwarded(10);
        result.setUnmatched(Arrays.asList("UnknownPlayer1", "UnknownPlayer2", "UnknownPlayer3"));
        return result;
    }

    /**
     * Mock rank summary for GET /api/members/ranks/summary
     */
    public static RankSummaryResponse getRankSummary() {
        RankSummaryResponse response = new RankSummaryResponse();
        response.setSuccess(true);
        response.setTotalMembers(42);
        
        List<RankSummaryResponse.RankCount> ranks = new ArrayList<>();
        
        RankSummaryResponse.RankCount maxed = new RankSummaryResponse.RankCount();
        maxed.setRank("maxed");
        maxed.setCount(8);
        ranks.add(maxed);
        
        RankSummaryResponse.RankCount hero = new RankSummaryResponse.RankCount();
        hero.setRank("hero");
        hero.setCount(12);
        ranks.add(hero);
        
        RankSummaryResponse.RankCount ruby = new RankSummaryResponse.RankCount();
        ruby.setRank("ruby");
        ruby.setCount(10);
        ranks.add(ruby);
        
        RankSummaryResponse.RankCount emerald = new RankSummaryResponse.RankCount();
        emerald.setRank("emerald");
        emerald.setCount(7);
        ranks.add(emerald);
        
        RankSummaryResponse.RankCount jade = new RankSummaryResponse.RankCount();
        jade.setRank("jade");
        jade.setCount(5);
        ranks.add(jade);
        
        response.setRanks(ranks);
        return response;
    }
}
