package com.boomerangbandits.eastereggs;

import com.boomerangbandits.api.models.PluginConfigResponse;
import com.boomerangbandits.services.ConfigSyncService;
import com.boomerangbandits.services.FeatureFlagService;
import com.google.common.collect.ImmutableSet;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class NPCRenameManager {
    private static final Set<MenuAction> NPC_MENU_ACTIONS = ImmutableSet.of(
            MenuAction.NPC_FIRST_OPTION,
            MenuAction.NPC_SECOND_OPTION,
            MenuAction.NPC_THIRD_OPTION,
            MenuAction.NPC_FOURTH_OPTION,
            MenuAction.NPC_FIFTH_OPTION,
            MenuAction.EXAMINE_NPC
    );

    private final EventBus eventBus;
    private final ConfigSyncService configSyncService;
    private final FeatureFlagService featureFlagService;
    private final Map<String, String> npcRenames = new HashMap<>();

    @Inject
    public NPCRenameManager(EventBus eventBus, ConfigSyncService configSyncService, FeatureFlagService featureFlagService) {
        this.eventBus = eventBus;
        this.configSyncService = configSyncService;
        this.featureFlagService = featureFlagService;
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!featureFlagService.isEasterEggsEnabled()) {
            return;
        }

        MenuEntry entry = event.getMenuEntry();
        if (entry == null || !NPC_MENU_ACTIONS.contains(entry.getType())) {
            return;
        }

        PluginConfigResponse latest = configSyncService.getLatestConfig();
        if (latest == null || latest.getNpcRenames() == null || latest.getNpcRenames().isEmpty()) {
            return;
        }

        npcRenames.clear();
        npcRenames.putAll(latest.getNpcRenames());

        String target = entry.getTarget();
        if (target == null || target.isEmpty()) {
            return;
        }

        String cleanTarget = Text.removeTags(target);
        String replacement = npcRenames.get(cleanTarget);
        if (replacement != null) {
            entry.setTarget(target.replace(cleanTarget, replacement));
        }
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        npcRenames.clear();
        eventBus.unregister(this);
    }
}
