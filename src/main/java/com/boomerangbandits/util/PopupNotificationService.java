package com.boomerangbandits.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.WidgetNode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Shows native OSRS notification popups (same widget as collection log notifications).
 * Uses interface 660 + script 3343 (NOTIFICATION_DISPLAY_INIT).
 * <p>
 * Popups are queued and processed one at a time on the game tick.
 * Call {@link #processQueue()} from onGameTick to drain the queue.
 */
@Slf4j
@Singleton
public class PopupNotificationService {
    private static final int SCRIPT_ID = 3343; // NOTIFICATION_DISPLAY_INIT
    private static final int INTERFACE_ID = 660;
    // Widget child 3 = title text (verified in-game)
    private static final int CHILD_TITLE_TEXT = 3;

    private static final int RESIZABLE_CLASSIC_LAYOUT = WidgetUtil.packComponentId(161, 13);
    private static final int RESIZABLE_MODERN_LAYOUT = WidgetUtil.packComponentId(164, 13);
    private static final int FIXED_CLASSIC_LAYOUT = WidgetUtil.packComponentId(548, 42);

    private static final Color DEFAULT_GOLD = new Color(0xFF, 0xD7, 0x00);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private final Queue<PopupData> popupQueue = new ArrayDeque<>();

    /**
     * Queue a popup with full customisation.
     *
     * @param title      popup title line
     * @param message    popup body line
     * @param color      text color for both title and body (passed to script), or null for default gold
     * @param titleColor override color for title text only (post-modified via child 3), or null to match color
     * @param fontId     OSRS font ID, or -1 to keep default (reserved for future use)
     */
    public void showPopup(String title, String message,
                          @Nullable Color color, @Nullable Color titleColor, int fontId) {
        popupQueue.offer(new PopupData(title, message, color, titleColor, fontId));
    }

    /**
     * Queue a popup with separate title color, default font.
     */
    public void showPopup(String title, String message,
                          @Nullable Color color, @Nullable Color titleColor) {
        showPopup(title, message, color, titleColor, -1);
    }

    /**
     * Queue a popup with a single color for both title and body.
     */
    public void showPopup(String title, String message, @Nullable Color color) {
        showPopup(title, message, color, null, -1);
    }

    /**
     * Queue a popup with default gold color.
     */
    public void showPopup(String title, String message) {
        showPopup(title, message, DEFAULT_GOLD, null, -1);
    }

    /**
     * Call this from onGameTick. Processes one popup per tick if no popup is currently visible.
     */
    public void processQueue() {
        // Wait for any existing popup to close
        if (client.getWidget(INTERFACE_ID, 1) != null) {
            return;
        }

        if (popupQueue.isEmpty()) {
            return;
        }

        PopupData data = popupQueue.poll();
        if (data == null) {
            return;
        }

        try {
            WidgetNode widgetNode = client.openInterface(getComponentId(), INTERFACE_ID, WidgetModalMode.MODAL_CLICKTHROUGH);

            Color color = data.color != null ? data.color : DEFAULT_GOLD;
            client.runScript(SCRIPT_ID, data.title, data.message, toRgbInt(color));

            // Post-modify: override title color if specified (child 3 = title text)
            if (data.titleColor != null) {
                Widget titleWidget = client.getWidget(INTERFACE_ID, CHILD_TITLE_TEXT);
                if (titleWidget != null) {
                    titleWidget.setTextColor(toRgbInt(data.titleColor));
                }
            }
            Widget widget = client.getWidget(INTERFACE_ID, 1);
            clientThread.invokeLater(() -> {
                if (widget != null && widget.getWidth() > 0) {
                    return false; // still visible, keep waiting
                }
                client.closeInterface(widgetNode, true);
                return true;
            });
        } catch (Exception e) {
            log.warn("Failed to show popup notification", e);
        }
    }

    private int getComponentId() {
        return client.isResized()
                ? (client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
                    ? RESIZABLE_MODERN_LAYOUT
                    : RESIZABLE_CLASSIC_LAYOUT)
                : FIXED_CLASSIC_LAYOUT;
    }

    private static int toRgbInt(Color color) {
        return color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
    }

    private static class PopupData {
        final String title;
        final String message;
        final Color color;
        final Color titleColor;
        final int fontId;

        PopupData(String title, String message,
                 @Nullable Color color, @Nullable Color titleColor, int fontId) {
            this.title = title;
            this.message = message;
            this.color = color;
            this.titleColor = titleColor;
            this.fontId = fontId;
        }
    }
}
