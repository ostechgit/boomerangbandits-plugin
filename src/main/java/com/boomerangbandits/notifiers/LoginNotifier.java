package com.boomerangbandits.notifiers;

import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Tracks player logins.
 * <p>
 * Called directly by plugin (not via EventBus).
 */
@Slf4j
@Singleton
public class LoginNotifier extends BaseNotifier {

    @Override
    protected boolean isEnabled() {
        return false;
    }

    @Override
    protected String getEventType() {
        return "LOGIN";
    }

    public void onLogin() {
        Map<String, Object> eventData = new HashMap<>();
        sendNotification(eventData);
    }
}
