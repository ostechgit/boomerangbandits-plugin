package com.boomerangbandits.services;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delivers announcements as in-game chat messages via ChatMessageManager.
 * <p>
 * Complements the sidebar display in HomePanel — players see announcements
 * even when the plugin panel isn't open.
 * <p>
 * Deduplication: announcements are tracked by content hash so the same
 * message is never shown twice per session (survives config sync polls).
 * Resets on logout so returning players see current announcements fresh.
 */
@Slf4j
@Singleton
public class InGameAnnouncementService {

    private static final String PREFIX = "[Boomerang Bandits]";

    @Inject
    private ChatMessageManager chatMessageManager;

    /**
     * Content hashes of announcements already delivered this session.
     * Using content hash (not index) so reordering the list on the backend
     * doesn't cause duplicate deliveries.
     */
    private final Set<Integer> deliveredHashes = new HashSet<>();

    /**
     * Deliver any new announcements to in-game chat.
     * Safe to call repeatedly — only undelivered messages are shown.
     *
     * @param announcements current announcement list from config
     */
    public void deliverAnnouncements(List<String> announcements) {
        if (announcements == null || announcements.isEmpty()) {
            return;
        }

        for (String message : announcements) {
            if (message == null || message.trim().isEmpty()) {
                continue;
            }

            int hash = message.hashCode();
            if (deliveredHashes.contains(hash)) {
                continue;
            }

            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.BROADCAST)
                    .runeLiteFormattedMessage(
                            "<col=FFD700>" + PREFIX + "</col> " + message)
                    .build());

            deliveredHashes.add(hash);
            log.debug("Delivered in-game announcement: {}", truncate(message));
        }
    }

    /**
     * Reset tracking state. Call on logout so returning players
     * see current announcements fresh on their next session.
     */
    public void reset() {
        deliveredHashes.clear();
    }

    private static String truncate(String s) {
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
