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
public class ItemRenameManager {
    private static final Set<MenuAction> ITEM_MENU_ACTIONS = ImmutableSet.of(
            MenuAction.GROUND_ITEM_FIRST_OPTION,
            MenuAction.GROUND_ITEM_SECOND_OPTION,
            MenuAction.GROUND_ITEM_THIRD_OPTION,
            MenuAction.GROUND_ITEM_FOURTH_OPTION,
            MenuAction.GROUND_ITEM_FIFTH_OPTION,
            MenuAction.EXAMINE_ITEM_GROUND,
            MenuAction.CC_OP,
            MenuAction.CC_OP_LOW_PRIORITY,
            MenuAction.WIDGET_TARGET,
            MenuAction.WIDGET_TARGET_ON_PLAYER,
            MenuAction.WIDGET_TARGET_ON_NPC,
            MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
            MenuAction.WIDGET_TARGET_ON_GROUND_ITEM,
            MenuAction.WIDGET_TARGET_ON_WIDGET
    );

    private final EventBus eventBus;
    private final ConfigSyncService configSyncService;
    private final FeatureFlagService featureFlagService;
    private final Map<String, String> itemRenames = new HashMap<>();

    @Inject
    public ItemRenameManager(EventBus eventBus, ConfigSyncService configSyncService, FeatureFlagService featureFlagService) {
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
        if (entry == null || !ITEM_MENU_ACTIONS.contains(entry.getType())) {
            return;
        }

        PluginConfigResponse latest = configSyncService.getLatestConfig();
        if (latest == null || latest.getItemRenames() == null || latest.getItemRenames().isEmpty()) {
            return;
        }

        itemRenames.clear();
        itemRenames.putAll(latest.getItemRenames());

        String target = entry.getTarget();
        if (target == null || target.isEmpty()) {
            return;
        }

        String cleanTarget = Text.removeTags(target);
        String replacement = itemRenames.get(cleanTarget);
        if (replacement != null) {
            entry.setTarget(target.replace(cleanTarget, replacement));
        }
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        itemRenames.clear();
        eventBus.unregister(this);
    }
}
