package com.boomerangbandits;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches RuneLite with the Boomerang Bandits plugin loaded.
 * Run via: ./gradlew run
 */
public class BoomerangBanditsPluginTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(BoomerangBanditsPlugin.class);
        RuneLite.main(args);
    }
}
