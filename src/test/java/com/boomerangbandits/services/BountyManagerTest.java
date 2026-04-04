package com.boomerangbandits.services;

import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.util.GameModeGuard;
import com.boomerangbandits.util.PopupNotificationService;
import com.boomerangbandits.util.ScreenshotService;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collection;
import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BountyManagerTest {

    @Mock private EventBus eventBus;
    @Mock private ConfigSyncService configSyncService;
    @Mock private ScreenshotService screenshotService;
    @Mock private ChatMessageManager chatMessageManager;
    @Mock private Client client;
    @Mock private FeatureFlagService featureFlagService;
    @Mock private ClanApiService clanApi;
    @Mock private PopupNotificationService popupService;
    @Mock private GameModeGuard gameModeGuard;

    @Mock private NPC npc;
    @Mock private Player player;

    private BountyManager bountyManager;

    @Before
    public void setUp() {
        bountyManager = new BountyManager(
                eventBus, configSyncService, screenshotService,
                chatMessageManager, client, featureFlagService, clanApi, popupService,
                gameModeGuard
        );
    }

    @Test
    public void onNpcSpawned_nonStandardWorld_doesNotProceed() {
        when(gameModeGuard.isStandardWorld()).thenReturn(false);

        bountyManager.onNpcSpawned(new NpcSpawned(npc));

        verify(featureFlagService, never()).isBountyTrackingEnabled();
    }

    @Test
    public void onChatMessage_nonStandardWorld_noApiCall() {
        when(gameModeGuard.isStandardWorld()).thenReturn(false);

        ChatMessage event = new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "",
                "You have a funny feeling like you're being followed", "", 0);

        bountyManager.onChatMessage(event);

        verify(featureFlagService, never()).isBountyTrackingEnabled();
        verify(clanApi, never()).submitBountyCompletion(any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    public void onNpcLootReceived_nonStandardWorld_noProcessing() {
        when(gameModeGuard.isStandardWorld()).thenReturn(false);

        Collection<ItemStack> items = Collections.singletonList(new ItemStack(4151, 1, null));
        NpcLootReceived event = new NpcLootReceived(npc, items);

        bountyManager.onNpcLootReceived(event);

        verify(featureFlagService, never()).isBountyTrackingEnabled();
    }

    @Test
    public void onPlayerLootReceived_nonStandardWorld_noProcessing() {
        when(gameModeGuard.isStandardWorld()).thenReturn(false);

        Collection<ItemStack> items = Collections.singletonList(new ItemStack(4151, 1, null));
        PlayerLootReceived event = new PlayerLootReceived(player, items);

        bountyManager.onPlayerLootReceived(event);

        verify(featureFlagService, never()).isBountyTrackingEnabled();
    }

    @Test
    public void onLootReceived_nonStandardWorld_noProcessing() {
        when(gameModeGuard.isStandardWorld()).thenReturn(false);

        Collection<ItemStack> items = Collections.singletonList(new ItemStack(4151, 1, null));
        LootReceived event = new LootReceived("The Whisperer", 0, LootRecordType.NPC, items, 1, null);

        bountyManager.onLootReceived(event);

        verify(featureFlagService, never()).isBountyTrackingEnabled();
    }

    @Test
    public void onNpcSpawned_standardWorld_proceedsToFeatureFlagCheck() {
        when(gameModeGuard.isStandardWorld()).thenReturn(true);
        when(featureFlagService.isBountyTrackingEnabled()).thenReturn(false);

        bountyManager.onNpcSpawned(new NpcSpawned(npc));

        verify(featureFlagService).isBountyTrackingEnabled();
    }

    @Test
    public void onChatMessage_standardWorld_proceedsToFeatureFlagCheck() {
        when(gameModeGuard.isStandardWorld()).thenReturn(true);
        when(featureFlagService.isBountyTrackingEnabled()).thenReturn(false);

        ChatMessage event = new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", "", "", 0);

        bountyManager.onChatMessage(event);

        verify(featureFlagService).isBountyTrackingEnabled();
    }

    @Test
    public void onNpcLootReceived_standardWorld_proceedsToFeatureFlagCheck() {
        when(gameModeGuard.isStandardWorld()).thenReturn(true);
        when(featureFlagService.isBountyTrackingEnabled()).thenReturn(false);

        Collection<ItemStack> items = Collections.singletonList(new ItemStack(4151, 1, null));
        NpcLootReceived event = new NpcLootReceived(npc, items);

        bountyManager.onNpcLootReceived(event);

        verify(featureFlagService).isBountyTrackingEnabled();
    }

    @Test
    public void onPlayerLootReceived_standardWorld_proceedsToFeatureFlagCheck() {
        when(gameModeGuard.isStandardWorld()).thenReturn(true);
        when(featureFlagService.isBountyTrackingEnabled()).thenReturn(false);

        Collection<ItemStack> items = Collections.singletonList(new ItemStack(4151, 1, null));
        PlayerLootReceived event = new PlayerLootReceived(player, items);

        bountyManager.onPlayerLootReceived(event);

        verify(featureFlagService).isBountyTrackingEnabled();
    }

    @Test
    public void onLootReceived_standardWorld_proceedsToFeatureFlagCheck() {
        when(gameModeGuard.isStandardWorld()).thenReturn(true);
        when(featureFlagService.isBountyTrackingEnabled()).thenReturn(false);

        Collection<ItemStack> items = Collections.singletonList(new ItemStack(4151, 1, null));
        LootReceived event = new LootReceived("The Whisperer", 0, LootRecordType.NPC, items, 1, null);

        bountyManager.onLootReceived(event);

        verify(featureFlagService).isBountyTrackingEnabled();
    }
}
