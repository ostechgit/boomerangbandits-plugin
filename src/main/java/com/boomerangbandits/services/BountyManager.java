package com.boomerangbandits.services;

import com.boomerangbandits.api.models.PluginConfigResponse;
import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.util.ScreenshotService;
import com.boomerangbandits.util.PopupNotificationService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.google.common.collect.ImmutableSet;
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
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Singleton
public class BountyManager {
    private static final long WINDOW_MS = 10_000;
    private static final int NEARBY_TILE_DISTANCE = 5;
    private static final String FOLLOWED_MESSAGE = "You have a funny feeling like you're being followed";
    private static final String WOULD_HAVE_BEEN_FOLLOWED_MESSAGE = "You have a funny feeling like you would have been followed";

    // NPCs that fire LootReceived (with NPC type) but NOT NpcLootReceived.
    // Must be handled via LootReceived fallback.
    private static final Set<String> SPECIAL_LOOT_NPC_NAMES = ImmutableSet.of(
            "The Whisperer",
            "Araxxor",
            "Crystalline Hunllef",
            "Corrupted Hunllef",
            "Branda the Fire Queen",
            "Eldric the Ice King"
    );
    private final EventBus eventBus;
    private final ConfigSyncService configSyncService;
    private final ScreenshotService screenshotService;
    private final ChatMessageManager chatMessageManager;
    private final Client client;
    private final FeatureFlagService featureFlagService;
    private final ClanApiService clanApi;
    private final PopupNotificationService popupService;
    private final ConcurrentLinkedQueue<NpcSpawnEntry> recentNpcSpawns = new ConcurrentLinkedQueue<>();

    @Inject
    public BountyManager(
            EventBus eventBus,
            ConfigSyncService configSyncService,
            ScreenshotService screenshotService,
            ChatMessageManager chatMessageManager,
            Client client,
            FeatureFlagService featureFlagService,
            ClanApiService clanApi,
            PopupNotificationService popupService
    ) {
        this.eventBus = eventBus;
        this.configSyncService = configSyncService;
        this.screenshotService = screenshotService;
        this.chatMessageManager = chatMessageManager;
        this.client = client;
        this.featureFlagService = featureFlagService;
        this.clanApi = clanApi;
        this.popupService = popupService;
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
    public void onNpcLootReceived(NpcLootReceived event) {
        checkLootForBounty(event.getItems());
    }

    @Subscribe
    public void onPlayerLootReceived(PlayerLootReceived event) {
        checkLootForBounty(event.getItems());
    }

    /**
     * Fallback for special NPCs (Whisperer, Araxxor, Hunllef, etc.) that fire
     * LootReceived but NOT NpcLootReceived. Requires Loot Tracker plugin enabled.
     * Regular NPC loot is filtered out to avoid double-processing.
     */
    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (event.getType() != LootRecordType.NPC || !SPECIAL_LOOT_NPC_NAMES.contains(event.getName())) {
            return;
        }
        checkLootForBounty(event.getItems());
    }

    private void checkLootForBounty(Collection<ItemStack> items) {
        if (!featureFlagService.isBountyTrackingEnabled() || items == null || items.isEmpty()) {
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

            for (PluginConfigResponse.BountyItem bountyItem : bounty.getItems()) {
                if (bountyItem == null || bountyItem.getItemId() <= 0) {
                    continue;
                }

                // Skip pet-mode items — those are handled via chat + NPC spawn correlation
                if (bountyItem.getNpcIds() != null && !bountyItem.getNpcIds().isEmpty()) {
                    continue;
                }

                if (hasLootItemMatch(items, bountyItem.getItemId())) {
                    onBountyCompleted(bounty, bountyItem);
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

    private boolean hasLootItemMatch(Collection<ItemStack> items, int itemId) {
        return items.stream().anyMatch(itemStack -> itemStack.getId() == itemId);
    }

    private void onBountyCompleted(PluginConfigResponse.Bounty bounty, PluginConfigResponse.BountyItem item) {
        String bountyId = bounty.getId() != null ? bounty.getId() : "unknown";
        String bountyName = bounty.getName() != null && !bounty.getName().trim().isEmpty() ? bounty.getName() : bountyId;
        String itemName = item.getName() != null ? item.getName() : "Unknown Item";
        int itemId = item.getItemId();
        String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown";

        screenshotService.captureScreenshot().thenAccept(base64 -> {
            log.info("Bounty completed: {} - {} (rsn={})", bountyId, itemName, rsn);

            // Show native OSRS popup (same as collection log notification)
            popupService.showPopup("Bounty Complete", "<col=FFFFFF>" + bountyName + "</col>:<br>" + itemName);

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
