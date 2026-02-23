package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.WomApiService;
import com.boomerangbandits.api.models.WomCompetition;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically refreshes WOM competition data.
 * <p>
 * Schedule:
 *   - Competition list: every 10 minutes
 *   - Active competition details: every 5 minutes (only when an active competition exists)
 * <p>
 * Started from BoomerangBanditsPlugin.startUp() AFTER authentication succeeds.
 * Stopped from shutDown().
 */
@Slf4j
@Singleton
public class CompetitionScheduler {

    private static final long LIST_INTERVAL_MINUTES = 10;
    private static final long DETAIL_INTERVAL_MINUTES = 5;

    private final WomApiService womApi;
    private final BoomerangBanditsConfig config;

    private ScheduledFuture<?> listTask;
    private ScheduledFuture<?> detailTask;

    /**
     * -- GETTER --
     *  Get the currently tracked active competition ID.
     *  Returns -1 if no active competition.
     */
    // Track the currently active competition ID for detail polling
    @Getter
    private volatile int activeCompetitionId = -1;

    @Inject
    public CompetitionScheduler(WomApiService womApi, BoomerangBanditsConfig config) {
        this.womApi = womApi;
        this.config = config;
    }

    /**
     * Start periodic competition polling.
     * Call from plugin startUp() after auth succeeds.
     *
     * @param executor the plugin's shared ScheduledExecutorService
     */
    public void start(ScheduledExecutorService executor) {
        log.debug("Starting CompetitionScheduler tasks...");
        
        // Fetch competition list every 10 minutes, starting immediately
        listTask = executor.scheduleAtFixedRate(
            this::refreshCompetitionList,
            0,
            LIST_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        );
        log.debug("Scheduled competition list task");

        // Fetch active competition details every 5 minutes, starting after 30s
        detailTask = executor.scheduleAtFixedRate(
            this::refreshActiveCompetition,
            30,
            DETAIL_INTERVAL_MINUTES * 60,
            TimeUnit.SECONDS
        );
        log.debug("Scheduled competition detail task");

        log.info("CompetitionScheduler started");
    }

    /**
     * Stop all polling tasks.
     * Call from plugin shutDown().
     */
    public void stop() {
        if (listTask != null) {
            listTask.cancel(false);
            listTask = null;
        }
        if (detailTask != null) {
            detailTask.cancel(false);
            detailTask = null;
        }
        activeCompetitionId = -1;
        log.info("CompetitionScheduler stopped");
    }

    private void refreshCompetitionList() {
        log.debug("refreshCompetitionList() called - fetching from WOM API");
        womApi.fetchCompetitions(
            competitions -> {
                log.debug("Received {} competitions from WOM API", competitions.size());
                // Find the first ongoing competition for detail polling
                int ongoingCount = 0;
                int upcomingCount = 0;
                int finishedCount = 0;
                for (WomCompetition comp : competitions) {
                    String status = comp.getStatus();
                    log.debug("Competition: {} - Status: {} (starts: {}, ends: {})", 
                        comp.getTitle(), status, comp.getStartsAt(), comp.getEndsAt());
                    
                    if (comp.isOngoing()) {
                        if (activeCompetitionId == -1) {
                            activeCompetitionId = comp.getId();
                            log.info("✅ Set active competition: {} (ID: {})", comp.getTitle(), comp.getId());
                        }
                        ongoingCount++;
                    } else if (comp.isUpcoming()) {
                        upcomingCount++;
                    } else if (comp.isFinished()) {
                        finishedCount++;
                    }
                }
                if (ongoingCount == 0) {
                    activeCompetitionId = -1;
                    log.debug("No ongoing competitions found");
                }
                log.info("✅ Competition list fetched: {} total ({} ongoing, {} upcoming, {} finished)", 
                    competitions.size(), ongoingCount, upcomingCount, finishedCount);
            },
            error -> log.warn("Failed to refresh competition list", error)
        );
    }

    private void refreshActiveCompetition() {
        int compId = activeCompetitionId;
        if (compId == -1) {
            return; // No active competition
        }

        womApi.fetchCompetitionDetails(
            compId,
            competition -> log.debug("Refreshed active competition: {}", competition.getTitle()),
            error -> log.warn("Failed to refresh competition {}", compId, error)
        );
    }

}
