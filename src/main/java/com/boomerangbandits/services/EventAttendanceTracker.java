package com.boomerangbandits.services;

/*
 * Attendance tracking logic adapted from:
 *   Clan Event Attendance by Jonathan Rousseau (JoRouss)
 *   https://github.com/JoRouss/runelite-ClanEventAttendance
 *   BSD 2-Clause License
 *
 * Changes from original:
 *  - Removed panel/UI, config, clipboard, and text-generation code
 *  - Tracks clan chat only (no friends chat option needed)
 *  - Exposes structured AttendanceEntry list instead of formatted text
 *  - Integrated into BoomerangBanditsPlugin event lifecycle
 */

import com.boomerangbandits.api.models.AttendanceEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.util.Text;

/**
 * Tracks clan member attendance during an in-game event.
 *
 * Usage:
 *   startEvent()  — call when admin starts the event
 *   stopEvent()   — call when admin stops; returns the attendance list
 *   onGameTick()  — call every game tick while running
 *   onPlayerSpawned/Despawned/ClanMemberJoined/Left — forward from plugin
 */
@Slf4j
@Singleton
public class EventAttendanceTracker {

    /** Minimum seconds a member must be present to count as "attended". */
    public static final int DEFAULT_PRESENT_THRESHOLD_SECONDS = 60 * 10; // 10 min
    /** Seconds after event start before a member is considered "late". */
    public static final int DEFAULT_LATE_THRESHOLD_SECONDS = 60 * 5;     // 5 min

    @Inject
    private Client client;

    // keyed by normalized (Jagex) lowercase name
    private final Map<String, MemberAttendance> buffer = new TreeMap<>();

    @Getter
    private boolean running = false;

    private int eventStartTick;
    private int eventStopTick;

    // Delay one tick after login/hop before scanning, same as original
    private int scanDelay = 0;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void startEvent() {
        buffer.clear();
        eventStartTick = client.getTickCount();
        running = true;
        scanDelay = 1;
        log.info("[Attendance] Event started at tick {}", eventStartTick);
    }

    /**
     * Stop the event and return the structured attendance list.
     * @param presentThresholdSeconds members below this are still included but flagged
     * @param lateThresholdSeconds    members who arrived after this are flagged as late
     */
    public List<AttendanceEntry> stopEvent(int presentThresholdSeconds, int lateThresholdSeconds) {
        // Compile any still-present members
        for (String key : buffer.keySet()) {
            compileTicks(key);
        }
        eventStopTick = client.getTickCount();
        running = false;

        List<AttendanceEntry> entries = new ArrayList<>(buffer.size());
        for (MemberAttendance ma : buffer.values()) {
            int secondsPresent = ticksToSeconds(ma.ticksTotal);
            int secondsLate = ticksToSeconds(ma.ticksLate);
            boolean late = secondsLate > lateThresholdSeconds;
            boolean meetsThreshold = secondsPresent >= presentThresholdSeconds;
            entries.add(new AttendanceEntry(
                ma.playerName,
                secondsPresent,
                late ? secondsLate : 0,
                meetsThreshold
            ));
        }

        log.info("[Attendance] Event stopped. {} members tracked, {} meet threshold.",
            entries.size(),
            entries.stream().filter(AttendanceEntry::isMeetsThreshold).count());

        return entries;
    }

    /** Convenience overload using defaults. */
    public List<AttendanceEntry> stopEvent() {
        return stopEvent(DEFAULT_PRESENT_THRESHOLD_SECONDS, DEFAULT_LATE_THRESHOLD_SECONDS);
    }

    public int getEventDurationSeconds() {
        int endTick = running ? client.getTickCount() : eventStopTick;
        return ticksToSeconds(endTick - eventStartTick);
    }

    public int getMemberCount() {
        return buffer.size();
    }

    // -------------------------------------------------------------------------
    // Event handlers — forward these from BoomerangBanditsPlugin
    // -------------------------------------------------------------------------

    public void onGameTick() {
        if (!running) return;

        if (scanDelay == 0) {
            // Refresh clan member list and scan visible players
            // TODO: Update deprecated getpPlayers() usage
            if (client.getClanChannel() != null) {
                for (final Player player : client.getPlayers()) {
                    if (player == null) continue;
                    if (isClanMember(player)) {
                        addPlayer(player);
                        unpausePlayer(player.getName());
                    }
                }
            }
        }

        if (scanDelay >= 0) {
            --scanDelay;
        }

        // Accumulate ticks for all tracked members
        for (String key : buffer.keySet()) {
            compileTicks(key);
        }
    }

    public void onPlayerSpawned(Player player) {
        if (!running) return;
        if (!isClanMember(player)) return;
        addPlayer(player);
        unpausePlayer(player.getName());
    }

    public void onPlayerDespawned(Player player) {
        if (!running) return;
        String key = nameToKey(player.getName());
        if (!buffer.containsKey(key)) return;
        compileTicks(player.getName());
        pausePlayer(player.getName());
    }

    public void onClanMemberJoined(ClanChannelMember member) {
        if (!running) return;
        if (member.getWorld() != client.getWorld()) return;

        String memberName = member.getName();
        for (Player player : client.getPlayers()) {
            if (player == null) continue;
            if (nameToKey(memberName).equals(nameToKey(player.getName()))) {
                addPlayer(player);
                unpausePlayer(player.getName());
                break;
            }
        }
    }

    public void onClanMemberLeft(ClanChannelMember member) {
        if (!running) return;
        if (member.getWorld() != client.getWorld()) return;

        String key = nameToKey(member.getName());
        if (!buffer.containsKey(key)) return;
        compileTicks(member.getName());
        pausePlayer(member.getName());
    }

    public void onHoppingOrLogin() {
        scanDelay = 1;
    }

    // -------------------------------------------------------------------------
    // Internal helpers (ported verbatim from original plugin)
    // -------------------------------------------------------------------------

    private boolean isClanMember(Player player) {
        return player.isClanMember();
    }

    private void addPlayer(Player player) {
        String key = nameToKey(player.getName());
        if (!buffer.containsKey(key)) {
            MemberAttendance ma = new MemberAttendance(
                player.getName(),
                client.getTickCount() - eventStartTick, // ticksLate
                client.getTickCount(),                   // tickActivityStarted
                0,                                       // ticksTotal
                false                                    // isPresent
            );
            buffer.put(key, ma);
        }
    }

    private void pausePlayer(String playerName) {
        MemberAttendance ma = buffer.get(nameToKey(playerName));
        if (ma != null) ma.isPresent = false;
    }

    private void unpausePlayer(String playerName) {
        MemberAttendance ma = buffer.get(nameToKey(playerName));
        if (ma == null || ma.isPresent) return;
        ma.isPresent = true;
        ma.tickActivityStarted = client.getTickCount();
    }

    private void compileTicks(String playerName) {
        MemberAttendance ma = buffer.get(nameToKey(playerName));
        if (ma == null || !ma.isPresent) return;
        ma.ticksTotal += client.getTickCount() - ma.tickActivityStarted;
        ma.tickActivityStarted = client.getTickCount();
    }

    private int ticksToSeconds(int ticks) {
        return (int) (ticks * 0.6f);
    }

    private String nameToKey(String name) {
        return Text.toJagexName(name).toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Internal data class
    // -------------------------------------------------------------------------

    private static class MemberAttendance {
        String playerName;
        int ticksLate;
        int tickActivityStarted;
        int ticksTotal;
        boolean isPresent;

        MemberAttendance(String playerName, int ticksLate, int tickActivityStarted,
                         int ticksTotal, boolean isPresent) {
            this.playerName = playerName;
            this.ticksLate = ticksLate;
            this.tickActivityStarted = tickActivityStarted;
            this.ticksTotal = ticksTotal;
            this.isPresent = isPresent;
        }
    }
}
