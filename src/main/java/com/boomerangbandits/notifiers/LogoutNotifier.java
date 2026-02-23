package com.boomerangbandits.notifiers;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks player logouts.
 *
 * Called directly by plugin (not via EventBus).
 */
@Slf4j
@Singleton
public class LogoutNotifier extends BaseNotifier {

    @Override
    protected boolean isEnabled() {
        return false;
    }

    @Override
    protected String getEventType() {
        return "LOGOUT";
    }

    public void onLogout() {
        if (shouldNotify()) {
            return;
        }
        Map<String, Object> eventData = new HashMap<>();
        sendNotification(eventData);
    }
}
