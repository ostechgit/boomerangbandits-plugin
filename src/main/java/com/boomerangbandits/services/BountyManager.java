package com.boomerangbandits.services;

import com.boomerangbandits.api.models.PluginConfigResponse;
import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.util.ScreenshotService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Singleton
public class BountyManager {
    private static final long WINDOW_MS = 10_000;
    private static final int NEARBY_TILE_DISTANCE = 5;
    private static final String FOLLOWED_MESSAGE = "You have a funny feeling like you're being followed";
    private static final String WOULD_HAVE_BEEN_FOLLOWED_MESSAGE = "You have a funny feeling like you would have been followed";

    private final EventBus eventBus;
    private final ConfigSyncService configSyncService;
    private final ScreenshotService screenshotService;
    private final ChatMessageManager chatMessageManager;
    private final Client client;
    private final FeatureFlagService featureFlagService;
    private final ClanApiService clanApi;
    private final ConcurrentLinkedQueue<NpcSpawnEntry> recentNpcSpawns = new ConcurrentLinkedQueue<>();

    @Inject
    public BountyManager(
            EventBus eventBus,
            ConfigSyncService configSyncService,
            ScreenshotService screenshotService,
            ChatMessageManager chatMessageManager,
            Client client,
            FeatureFlagService featureFlagService,
            ClanApiService clanApi
    ) {
        this.eventBus = eventBus;
        this.configSyncService = configSyncService;
        this.screenshotService = screenshotService;
        this.chatMessageManager = chatMessageManager;
        this.client = client;
        this.featureFlagService = featureFlagService;
        this.clanApi = clanApi;
    }

    @Getter
	public static class NpcSpawnEntry {
        private final int npcId;
        private final long timestamp;

        public NpcSpawnEntry(int npcId, long timestamp) {
            this.npcId = npcId;
            this.timestamp = timestamp;
        }

	}

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (!featureFlagService.isBountyTrackingEnabled()) {
            return;
        }

        NPC npc = event.getNpc();
        Player localPlayer = client.getLocalPlayer();
        if (npc == null || localPlayer == null) {
            return;
        }

        WorldPoint npcLocation = npc.getWorldLocation();
        WorldPoint playerLocation = localPlayer.getWorldLocation();
        if (npcLocation == null || playerLocation == null) {
            return;
        }

        if (npcLocation.distanceTo(playerLocation) > NEARBY_TILE_DISTANCE) {
            return;
        }

        recentNpcSpawns.add(new NpcSpawnEntry(npc.getId(), System.currentTimeMillis()));
        pruneStaleSpawns();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!featureFlagService.isBountyTrackingEnabled() || event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String message = Text.removeTags(event.getMessage());
        if (message == null ||
                (!message.contains(FOLLOWED_MESSAGE) && !message.contains(WOULD_HAVE_BEEN_FOLLOWED_MESSAGE))) {
            return;
        }

        pruneStaleSpawns();

        PluginConfigResponse latest = configSyncService.getLatestConfig();
        if (latest == null || latest.getBounties() == null || latest.getBounties().isEmpty()) {
            return;
        }

        for (PluginConfigResponse.Bounty bounty : latest.getBounties()) {
            if (bounty == null || bounty.getItems() == null) {
                continue;
            }

            for (PluginConfigResponse.BountyItem item : bounty.getItems()) {
                if (item == null || item.getNpcIds() == null || item.getNpcIds().isEmpty()) {
                    continue;
                }

                if (hasRecentSpawnMatch(item.getNpcIds())) {
                    onBountyCompleted(bounty, item);
                    return;
                }
            }
        }
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (!featureFlagService.isBountyTrackingEnabled() || event.getItems() == null || event.getItems().isEmpty()) {
            return;
        }

        PluginConfigResponse latest = configSyncService.getLatestConfig();
        if (latest == null || latest.getBounties() == null || latest.getBounties().isEmpty()) {
            return;
        }

        for (PluginConfigResponse.Bounty bounty : latest.getBounties()) {
            if (bounty == null || bounty.getItems() == null) {
                continue;
            }

            for (PluginConfigResponse.BountyItem item : bounty.getItems()) {
                if (item == null || item.getItemId() <= 0) {
                    continue;
                }

                if (item.getNpcIds() != null && !item.getNpcIds().isEmpty()) {
                    continue;
                }

                if (hasLootItemMatch(event, item.getItemId())) {
                    onBountyCompleted(bounty, item);
                    return;
                }
            }
        }
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        reset();
        eventBus.unregister(this);
    }

    public void reset() {
        recentNpcSpawns.clear();
    }

    private void pruneStaleSpawns() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        NpcSpawnEntry entry;
        while ((entry = recentNpcSpawns.peek()) != null && entry.getTimestamp() < cutoff) {
            recentNpcSpawns.poll();
        }
    }

    private boolean hasRecentSpawnMatch(List<Integer> npcIds) {
        for (NpcSpawnEntry spawnEntry : recentNpcSpawns) {
            if (npcIds.contains(spawnEntry.getNpcId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLootItemMatch(LootReceived event, int itemId) {
        return event.getItems().stream().anyMatch(itemStack -> itemStack.getId() == itemId);
    }

    private void onBountyCompleted(PluginConfigResponse.Bounty bounty, PluginConfigResponse.BountyItem item) {
        String bountyId = bounty.getId() != null ? bounty.getId() : "unknown";
        String itemName = item.getName() != null ? item.getName() : "Unknown Item";
        int itemId = item.getItemId();
        String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";

        screenshotService.captureScreenshot().thenAccept(base64 -> {
            log.info("Bounty completed: {} - {} (rsn={})", bountyId, itemName, rsn);

            chatMessageManager.queue(QueuedMessage.builder()
                    .type(ChatMessageType.BROADCAST)
                    .runeLiteFormattedMessage("<col=FFD700>[Boomerang Bandits]</col> Bounty completed: " + itemName)
                    .build());

            clanApi.submitBountyCompletion(bountyId, itemName, itemId, rsn, base64,
                    response -> {
                        if (response.isSuccess()) {
                            log.info("Bounty completion submitted to backend: {} - {}", bountyId, itemName);
                        } else {
                            log.warn("Backend rejected bounty completion: {} - {}: {}",
                                    bountyId, itemName, response.getMessage());
                        }
                    },
                    error -> log.warn("Failed to submit bounty completion: {} - {}: {}",
                            bountyId, itemName, error)
            );
        });
    }
}
