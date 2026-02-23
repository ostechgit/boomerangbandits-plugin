package com.boomerangbandits.notifiers;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.services.WebhookService;
import com.boomerangbandits.util.ClanValidator;
import com.boomerangbandits.util.PlayerDataCollector;
import com.google.gson.Gson;

import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

/**
 * Abstract base class for all event notifiers.
 *
 * Subclasses must implement:
 *   - isEnabled(): check config toggle for this notifier
 *   - getEventType(): return event type string (e.g., "LOOT", "LEVEL")
 *
 * Subclasses should:
 *   - Add @Subscribe methods for the RuneLite events they handle
 *   - Call sendNotification(eventData) with event-specific data
 *
 * The base class handles:
 *   - Validation (enabled check, clan check, auth check)
 *   - Building the full payload with player data
 *   - Sending to backend via WebhookService
 */
@Slf4j
public abstract class BaseNotifier {

    @Inject protected Client client;
    @Inject protected BoomerangBanditsConfig config;
    @Inject protected WebhookService webhookService;
    @Inject protected ClanValidator clanValidator;
    @Inject protected PlayerDataCollector playerDataCollector;
    @Inject protected ItemManager itemManager;
    @Inject protected Gson gson;

    protected abstract boolean isEnabled();

    protected abstract String getEventType();

    protected boolean shouldNotify() {
        if (!isEnabled()) {
            return true;
        }

        if (!clanValidator.validate()) {
            return true;
        }

        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            log.debug("Skipping {} notification — not authenticated", getEventType());
            return true;
        }

        return false;
    }

    protected void sendNotification(Map<String, Object> eventData) {
        if (shouldNotify()) {
            return;
        }

        Map<String, Object> payload = playerDataCollector.collectBaseData();
        payload.put("event_type", getEventType());
        payload.put("event_timestamp", System.currentTimeMillis());
        payload.put("data", eventData);
        payload.put("progression", playerDataCollector.collectProgressionMetrics());

        webhookService.send(payload,
            response -> log.debug("Event sent: {}", getEventType()),
            error -> log.warn("Failed to send {} event: {}", getEventType(), error)
        );
    }
}
