package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.api.models.PluginConfigResponse;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Periodically polls the backend for remote plugin configuration.
 * <p>
 * Lifecycle:
 * 1. Plugin calls start() AFTER successful authentication
 * 2. Polls GET /api/plugin/config every configSyncInterval seconds
 * 3. Updates hidden config items in RuneLite's ConfigManager
 * 4. UI reacts to ConfigChanged events automatically
 * 5. Plugin calls stop() on shutdown
 * <p>
 * Does NOT start before authentication. The memberCode is required.
 */
@Slf4j
@Singleton
public class ConfigSyncService {

    @Inject
    private ClanApiService clanApi;
    @Inject
    private BoomerangBanditsConfig config;
    @Inject
    private ConfigManager configManager;

    private ScheduledFuture<?> syncTask;

    /**
     * Start periodic config sync. Call only after authentication succeeds.
     *
     * @param executor the plugin's shared ScheduledExecutorService
     */
    public void start(ScheduledExecutorService executor) {
        if (syncTask != null && !syncTask.isDone()) {
            log.debug("ConfigSyncService already running");
            return;
        }

        int intervalSeconds = config.configSyncInterval();

        // Small initial delay to allow the UI and session to stabilize after login (Finding B4)
        syncTask = executor.scheduleAtFixedRate(
                this::syncConfig,
                5,
                intervalSeconds,
                TimeUnit.SECONDS
        );

        log.info("Config sync started (interval: {}s)", intervalSeconds);
    }

    /**
     * Stop periodic config sync.
     */
    public void stop() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }

        log.info("Config sync stopped");
    }

    /**
     * Force an immediate config sync outside the normal schedule.
     */
    public void syncNow() {
        syncConfig();
    }

    private void syncConfig() {
        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            log.debug("Skipping config sync — not authenticated");
            return;
        }

        clanApi.fetchPluginConfig(memberCode,
                this::applyConfig,
                error -> log.warn("Config sync failed: {}", error)
        );
    }

    private void applyConfig(PluginConfigResponse remoteConfig) {
        setIfChanged("announcementMessage", remoteConfig.getAnnouncementMessage());
        setIfChanged("rollCallActive", String.valueOf(remoteConfig.isRollCallActive()));
        setIfChanged("websiteUrl", remoteConfig.getWebsiteUrl());
        setIfChanged("discordUrl", remoteConfig.getDiscordUrl());

        // Store the Dink dynamic config URL in our own config so the UI can display it.
        // Users can copy it from the Clan Hub panel and paste it into Dink's settings manually.
        String dinkConfigUrl = remoteConfig.getDinkConfigUrl();
        if (dinkConfigUrl != null && !dinkConfigUrl.isEmpty()) {
            setIfChanged("dinkConfigUrl", dinkConfigUrl);
        }

        log.debug("Config sync applied successfully");
    }

    /**
     * Only update the config value if it actually changed.
     * This prevents unnecessary ConfigChanged events from firing.
     */
    private void setIfChanged(String key, String newValue) {
        if (newValue == null) {
            return;
        }

        String currentValue = configManager.getConfiguration(BoomerangBanditsConfig.GROUP, key);
        if (!newValue.equals(currentValue)) {
            configManager.setConfiguration(BoomerangBanditsConfig.GROUP, key, newValue);
            log.debug("Config updated: {} = {}", key, newValue);
        }
    }
}
