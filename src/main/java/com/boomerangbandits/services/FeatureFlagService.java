package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.models.PluginConfigResponse;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public class FeatureFlagService {

    @Inject
    private ConfigSyncService configSyncService;

    @Inject
    private BoomerangBanditsConfig config;

    public boolean isEnabled(String featureName, Supplier<Boolean> userConfigGetter) {
        PluginConfigResponse latestConfig = configSyncService.getLatestConfig();
        if (latestConfig == null) {
            return userConfigGetter.get();
        }

        Map<String, Boolean> features = latestConfig.getFeatures();
        if (features == null || !features.containsKey(featureName)) {
            return userConfigGetter.get();
        }

        Boolean serverEnabled = features.get(featureName);
        if (Boolean.FALSE.equals(serverEnabled)) {
            return false;
        }

        return userConfigGetter.get();
    }

    public boolean isCofferSoundEnabled() {
        return isEnabled("cofferDepositSound", config::cofferDepositSound);
    }

    public boolean isEventOverlayEnabled() {
        return isEnabled("eventOverlay", config::showEventOverlay);
    }

    public boolean isEasterEggsEnabled() {
        return isEnabled("easterEggs", config::enableEasterEggs);
    }

    public boolean isBountyTrackingEnabled() {
        return isEnabled("bountyTracking", () -> true);
    }
}
