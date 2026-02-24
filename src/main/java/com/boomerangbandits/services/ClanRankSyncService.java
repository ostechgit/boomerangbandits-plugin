package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.ApiConstants;
import com.boomerangbandits.api.models.RankSyncRequest;
import com.boomerangbandits.api.models.RankSyncResponse;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service for syncing clan member ranks to the backend.
 * <p>
 * Features:
 * - Collects ranks from ALL clan members (including offline) using ClanSettings
 * - Normalizes rank names to canonical backend values
 * - Filters out bot accounts (names starting with [#)
 * - Batches updates (max 1000 per request)
 * - Rate limiting (3 requests per minute)
 * - Periodic sync (configurable interval)
 * - Manual sync on demand
 * - Detailed logging of rank distribution
 * <p>
 * Implementation follows WiseOldMan's approach:
 * - Uses ClanSettings.getMembers() to get full roster (not just online)
 * - Uses custom titles from ClanSettings.titleForRank() for rank names
 */
@Slf4j
@Singleton
public class ClanRankSyncService {

    private static final int MAX_BATCH_SIZE = 1000;
    private static final int RATE_LIMIT_REQUESTS = 3;
    private static final long RATE_LIMIT_WINDOW_MS = 60_000; // 1 minute
    private static final int DEFAULT_SYNC_INTERVAL_MINUTES = 30;
    private final List<Long> recentRequestTimes = Collections.synchronizedList(new ArrayList<>());
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private BoomerangBanditsConfig config;
    @Inject
    private @Named("boomerang") OkHttpClient httpClient;
    @Inject
    private Gson gson;
    private ScheduledFuture<?> periodicSyncTask;
    private ScheduledExecutorService executor;

    /**
     * Start periodic rank syncing.
     */
    public void start(ScheduledExecutorService executor) {
        if (periodicSyncTask != null && !periodicSyncTask.isCancelled()) {
            return; // Already running
        }

        this.executor = executor;
        log.info("Starting clan rank sync service (interval: {} minutes)", DEFAULT_SYNC_INTERVAL_MINUTES);

        periodicSyncTask = executor.scheduleAtFixedRate(
                this::syncRanksAsync,
                5, // Initial delay (minutes)
                DEFAULT_SYNC_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * Stop periodic syncing.
     */
    public void stop() {
        if (periodicSyncTask != null) {
            periodicSyncTask.cancel(false);
            periodicSyncTask = null;
            log.info("Stopped clan rank sync service");
        }
    }

    /**
     * Manually trigger a rank sync.
     * This is async and will call the callback when complete.
     */
    public void syncRanksManually(Consumer<RankSyncResponse> onSuccess, Consumer<Exception> onError) {
        log.info("Manual rank sync triggered");
        syncRanks(onSuccess, onError);
    }

    /**
     * Async wrapper for periodic sync (no callbacks).
     */
    private void syncRanksAsync() {
        syncRanks(
                response -> log.info("Rank sync complete: {} synced, {} updated, {} unchanged, {} created, {} not found",
                        response.getSynced(), response.getUpdated(), response.getUnchanged(),
                        response.getCreated(),
                        response.getNotFound() != null ? response.getNotFound().size() : 0),
                error -> log.warn("Rank sync failed", error)
        );
    }

    /**
     * Main sync logic - collects ranks and sends to backend.
     */
    private void syncRanks(Consumer<RankSyncResponse> onSuccess, Consumer<Exception> onError) {
        // Check rate limit and record atomically to prevent TOCTOU race
        synchronized (recentRequestTimes) {
            if (!checkRateLimit()) {
                log.warn("Rate limit exceeded - skipping rank sync");
                onError.accept(new Exception("Rate limit exceeded"));
                return;
            }
            recordRequest();
        }

        // Collect ranks on client thread
        clientThread.invoke(() -> {
            try {
                List<RankSyncRequest.RankUpdate> updates = collectRankUpdates();

                if (updates.isEmpty()) {
                    log.debug("No rank updates to sync");
                    return;
                }

                log.info("Collected {} rank updates, sending to backend", updates.size());

                // Send to backend via executor (off client thread)
                executor.submit(() -> sendRankUpdates(updates, onSuccess, onError));

            } catch (Exception e) {
                log.error("Error collecting rank updates", e);
                onError.accept(e);
            }
        });
    }

    /**
     * Collect rank updates from in-game clan roster.
     * MUST be called on client thread.
     * <p>
     * Uses ClanSettings.getMembers() to get ALL clan members (including offline),
     * not just online members from ClanChannel.
     */
    private List<RankSyncRequest.RankUpdate> collectRankUpdates() {
        List<RankSyncRequest.RankUpdate> updates = new ArrayList<>();

        ClanSettings clanSettings = client.getClanSettings();
        if (clanSettings == null) {
            log.debug("Clan settings not loaded - cannot collect ranks");
            return updates;
        }

        log.debug("[RankSync] Collecting ranks from clan: {}", clanSettings.getName());

        // Use ClanSettings.getMembers() to get ALL members (including offline)
        // This is what WOM does - it syncs the entire clan roster, not just online members
        List<net.runelite.api.clan.ClanMember> allMembers = clanSettings.getMembers();

        log.debug("[RankSync] Total clan members: {}", allMembers.size());

        // Track rank distribution for logging
        Map<String, Integer> rankCounts = new HashMap<>();

        for (net.runelite.api.clan.ClanMember member : allMembers) {
            String rsn = member.getName();

            // Skip bot accounts (names starting with [#)
            if (rsn.startsWith("[#")) {
                log.debug("[RankSync] Skipping bot account: {}", rsn);
                continue;
            }

            // Normalize RSN to Jagex format (spaces to underscores)
            // This matches WOM's approach and ensures consistent formatting
            String normalizedRsn = Text.toJagexName(rsn);

            ClanRank rank = member.getRank();
            int rankValue = rank.getRank();

            // Normalize rank to canonical backend value
            String canonicalRank = normalizeRank(rankValue, rank, clanSettings);

            if (canonicalRank != null) {
                updates.add(new RankSyncRequest.RankUpdate(normalizedRsn, canonicalRank));
                rankCounts.put(canonicalRank, rankCounts.getOrDefault(canonicalRank, 0) + 1);
            } else {
                log.debug("[RankSync] Skipping {} - unknown rank value {}", normalizedRsn, rankValue);
            }
        }

        // Log rank distribution at debug level only
        log.debug("[RankSync] Rank distribution: {}", rankCounts);

        return updates;
    }

    /**
     * Normalize a rank to canonical backend format using the clan title name.
     */
    private String normalizeRank(int rankValue, ClanRank rank, ClanSettings clanSettings) {
        ClanTitle title = clanSettings.titleForRank(rank);
        if (title != null && title.getName() != null && !title.getName().isEmpty()) {
            return title.getName().toLowerCase().replace("_", " ");
        }
        log.debug("[RankSync] No title found for rank value {}", rankValue);
        return null;
    }

    /**
     * Send rank updates to backend.
     * Handles batching if needed.
     */
    private void sendRankUpdates(List<RankSyncRequest.RankUpdate> updates,
                                 Consumer<RankSyncResponse> onSuccess,
                                 Consumer<Exception> onError) {

        // Batch if needed
        if (updates.size() > MAX_BATCH_SIZE) {
            log.info("Batching {} updates into chunks of {}", updates.size(), MAX_BATCH_SIZE);

            for (int i = 0; i < updates.size(); i += MAX_BATCH_SIZE) {
                int end = Math.min(i + MAX_BATCH_SIZE, updates.size());
                List<RankSyncRequest.RankUpdate> batch = updates.subList(i, end);

                log.info("Sending batch {}/{}", (i / MAX_BATCH_SIZE) + 1,
                        (updates.size() + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE);

                sendBatch(batch, onSuccess, onError);

                // Small delay between batches to avoid rate limiting
                if (end < updates.size()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } else {
            sendBatch(updates, onSuccess, onError);
        }
    }

    /**
     * Send a single batch to backend.
     */
    private void sendBatch(List<RankSyncRequest.RankUpdate> updates,
                           Consumer<RankSyncResponse> onSuccess,
                           Consumer<Exception> onError) {

        RankSyncRequest request = new RankSyncRequest();
        request.setUpdates(updates);
        request.setCreateIfNotFound(true);

        String json = gson.toJson(request);
        RequestBody body = RequestBody.create(ApiConstants.JSON, json);

        Request httpRequest = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/members/ranks/sync")
                .post(body)
                .addHeader("X-Member-Code", config.memberCode())
                .addHeader("Content-Type", "application/json")
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        httpClient.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Rank sync request failed", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "no body";
                        log.warn("Rank sync returned HTTP {}: {}", response.code(), errorBody);
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    assert response.body() != null;
                    String responseBody = response.body().string();
                    RankSyncResponse syncResponse = gson.fromJson(responseBody, RankSyncResponse.class);

                    // Single-line summary at INFO, details at debug
                    log.info("[RankSync] Done — synced:{} updated:{} unchanged:{} created:{}",
                            syncResponse.getSynced(), syncResponse.getUpdated(),
                            syncResponse.getUnchanged(), syncResponse.getCreated());
                    log.debug("[RankSync] Full response: {}", responseBody);

                    // Log not found members
                    if (syncResponse.getNotFound() != null && !syncResponse.getNotFound().isEmpty()) {
                        log.warn("[RankSync]   Not Found: {} members", syncResponse.getNotFound().size());
                        for (String rsn : syncResponse.getNotFound()) {
                            log.warn("[RankSync]     - {}", rsn);
                        }
                    }

                    // Log invalid entries
                    if (syncResponse.getInvalid() > 0) {
                        log.error("[RankSync]   Invalid: {} entries", syncResponse.getInvalid());
                    }

                    // Log detailed errors
                    if (syncResponse.getErrors() != null && !syncResponse.getErrors().isEmpty()) {
                        log.error("[RankSync] Errors:");
                        for (RankSyncResponse.SyncError error : syncResponse.getErrors()) {
                            String indexStr = error.getIndex() != null ? String.valueOf(error.getIndex()) : "?";

                            // Prefer rsnInput (raw input) over rsn (normalized)
                            String displayName = error.getRsnInput() != null ? error.getRsnInput() : error.getRsn();

                            if (displayName != null) {
                                log.error("[RankSync]   [{}] {} - {}", indexStr, displayName, error.getError());
                            } else {
                                log.error("[RankSync]   [{}] {}", indexStr, error.getError());
                            }
                        }
                    }

                    onSuccess.accept(syncResponse);

                } catch (Exception e) {
                    log.error("Error parsing rank sync response", e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Check if we're within rate limits.
     */
    private boolean checkRateLimit() {
        long now = System.currentTimeMillis();

        // Remove old requests outside the window
        recentRequestTimes.removeIf(time -> now - time > RATE_LIMIT_WINDOW_MS);

        // Check if we can make another request
        return recentRequestTimes.size() < RATE_LIMIT_REQUESTS;
    }

    /**
     * Record a request for rate limiting.
     */
    private void recordRequest() {
        recentRequestTimes.add(System.currentTimeMillis());
    }
}
