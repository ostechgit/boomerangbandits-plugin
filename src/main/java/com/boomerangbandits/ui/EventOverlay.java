package com.boomerangbandits.ui;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.ClanContentService;
import com.boomerangbandits.api.models.ActiveEvent;
import com.boomerangbandits.api.models.EventDetails;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

@Slf4j
public class EventOverlay extends OverlayPanel {
    private final ClanContentService contentService;
    private final BoomerangBanditsConfig config;
    
    private ActiveEvent currentEvent;
    private volatile long lastRefresh = 0;
    private static final long REFRESH_INTERVAL = 60_000; // 1 minute
    
    @Inject
    public EventOverlay(
        ClanContentService contentService,
        BoomerangBanditsConfig config
    ) {
        this.contentService = contentService;
        this.config = config;
        
        setPosition(OverlayPosition.TOP_LEFT);
        getMenuEntries().add(new OverlayMenuEntry(
            RUNELITE_OVERLAY_CONFIG,
            OPTION_CONFIGURE,
            "Boomerang Bandits Event Overlay"
        ));
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.getChildren().clear();
        
        // Check if overlay is enabled
        if (!config.showEventOverlay()) {
            return null;
        }
        
        // Refresh event data periodically
        long now = System.currentTimeMillis();
        if (now - lastRefresh > REFRESH_INTERVAL) {
            refreshEvent();
            lastRefresh = now;
        }
        
        // Check if there's an active event
        if (currentEvent == null || currentEvent.getEvents() == null || currentEvent.getEvents().isEmpty()) {
            return null;
        }

        // Use the first live event for the overlay
        EventDetails event = currentEvent.getEvents().stream()
            .filter(e -> {
                if (e.getStartTime() == null || e.getStartTime().isEmpty()) return true;
                try { return java.time.Instant.parse(e.getStartTime()).isBefore(java.time.Instant.now()); }
                catch (Exception ex) { return true; }
            })
            .findFirst()
            .orElse(currentEvent.getEvents().get(0));
        
        // Event name (title)
        panelComponent.getChildren().add(LineComponent.builder()
            .left(event.getName())
            .leftColor(Color.YELLOW)
            .build());
        
        // Event password
        if (event.getEventPassword() != null && !event.getEventPassword().isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Password:")
                .leftColor(Color.LIGHT_GRAY)
                .right(event.getEventPassword())
                .rightColor(Color.GREEN)
                .build());
        }
        
        // Challenge password
        if (event.getChallengePassword() != null && !event.getChallengePassword().isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Challenge:")
                .leftColor(Color.LIGHT_GRAY)
                .right(event.getChallengePassword())
                .rightColor(Color.ORANGE)
                .build());
        }
        
        // Location
        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Location:")
                .leftColor(Color.LIGHT_GRAY)
                .right(event.getLocation())
                .rightColor(Color.WHITE)
                .build());
        }
        
        // World
        if (event.getWorld() > 0) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("World:")
                .leftColor(Color.LIGHT_GRAY)
                .right(String.valueOf(event.getWorld()))
                .rightColor(Color.WHITE)
                .build());
        }
        
        // Time remaining
        if (event.getEndTime() != null && !event.getEndTime().isEmpty()) {
            String timeRemaining = calculateTimeRemaining(event.getEndTime());
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Ends in:")
                .leftColor(Color.LIGHT_GRAY)
                .right(timeRemaining)
                .rightColor(Color.CYAN)
                .build());
        }
        
        return super.render(graphics);
    }
    
    public void refreshEvent() {
        contentService.fetchActiveEvent(
            event -> this.currentEvent = event,
            error -> log.debug("Failed to fetch active event: {}", error.getMessage())
        );
    }
    
    private String calculateTimeRemaining(String endTimeStr) {
        try {
            ZonedDateTime endTime = ZonedDateTime.parse(endTimeStr, DateTimeFormatter.ISO_DATE_TIME);
            Instant endInstant = endTime.toInstant();
            Instant now = Instant.now();
            
            if (endInstant.isBefore(now)) {
                return "Ended";
            }
            
            Duration duration = Duration.between(now, endInstant);
            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            
            if (hours > 0) {
                return String.format("%dh %dm", hours, minutes);
            } else {
                return String.format("%dm", minutes);
            }
        } catch (Exception e) {
            log.warn("Failed to parse end time: {}", endTimeStr, e);
            return "Unknown";
        }
    }
}
