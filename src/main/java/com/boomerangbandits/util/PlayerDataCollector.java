package com.boomerangbandits.util;

import com.boomerangbandits.BoomerangBanditsConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.ItemManager;

/**
 * Collects common player data used by all notifiers.
 * <p>
 * Centralises data collection so notifiers don't duplicate logic.
 * Must be called on the game thread (or from @Subscribe handlers).
 */
@Slf4j
@Singleton
public class PlayerDataCollector {

    private static final int EQUIPMENT_CONTAINER_ID = 94;
    private static final int INVENTORY_CONTAINER_ID = 93;

    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private BoomerangBanditsConfig config;

    /**
     * Collect base player data included in every event payload.
     * Returns a map with: account_hash, username, world, location, region_id, accountType, ownerTag.
     *
     * <p>Must be called on the client thread. All client API reads are performed synchronously.
     */
    public Map<String, Object> collectBaseData() {
        Map<String, Object> data = new HashMap<>();

        data.put("account_hash", client.getAccountHash());
        data.put("username", client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName() : "Unknown");
        data.put("world", client.getWorld());

        if (client.getLocalPlayer() != null) {
            WorldPoint wp = client.getLocalPlayer().getWorldLocation();
            data.put("world_x", wp.getX());
            data.put("world_y", wp.getY());
            data.put("plane", wp.getPlane());
            data.put("region_id", wp.getRegionID());
        }

        // Derive account type from game client varbit
        int ironmanVarbit = client.getVarbitValue(net.runelite.api.gameval.VarbitID.IRONMAN);
        String accountType;
        if (ironmanVarbit == 1) {
            accountType = "ironman";
        } else if (ironmanVarbit == 2) {
            accountType = "hardcore_ironman";
        } else if (ironmanVarbit == 3) {
            accountType = "ultimate_ironman";
        } else if (ironmanVarbit == 4) {
            accountType = "group_ironman";
        } else if (ironmanVarbit == 5) {
            accountType = "hardcore_group_ironman";
        } else {
            accountType = "normal";
        }
        data.put("accountType", accountType);

        return data;
    }

    /**
     * Collect progression metrics for near-real-time analytics.
     * Includes current XP values that backend can use to calculate deltas.
     *
     * <p>Must be called on the client thread. All client API reads are performed synchronously.
     *
     * @return Map with progression.metrics structure
     */
    public Map<String, Object> collectProgressionMetrics() {
        Map<String, Object> progression = new HashMap<>();
        Map<String, Object> metrics = new HashMap<>();
        
        // Add overall XP
        if (client.getLocalPlayer() != null) {
            long totalXp = client.getOverallExperience();
            Map<String, Object> overallMetric = new HashMap<>();
            overallMetric.put("end", totalXp);
            metrics.put("overall", overallMetric);
        }
        
        progression.put("metrics", metrics);
        return progression;
    }

    /**
     * Get equipment data as a list of item maps.
     * Each item: {id, name, quantity, ge_price}
     */
    public List<Map<String, Object>> getEquipmentData() {
        return getContainerItems(EQUIPMENT_CONTAINER_ID);
    }

    /**
     * Get inventory data as a list of item maps.
     */
    public List<Map<String, Object>> getInventoryData() {
        return getContainerItems(INVENTORY_CONTAINER_ID);
    }

    private List<Map<String, Object>> getContainerItems(int containerId) {
        List<Map<String, Object>> items = new ArrayList<>();
        ItemContainer container = client.getItemContainer(containerId);

        if (container == null) {
            return items;
        }

        for (Item item : container.getItems()) {
            if (item.getId() > 0) {
                ItemComposition comp = itemManager.getItemComposition(item.getId());
                Map<String, Object> itemData = new HashMap<>();
                itemData.put("id", item.getId());
                itemData.put("name", comp.getName());
                itemData.put("quantity", item.getQuantity());
                itemData.put("ge_price", itemManager.getItemPrice(item.getId()));
                items.add(itemData);
            }
        }

        return items;
    }
}
