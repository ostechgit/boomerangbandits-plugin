package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import java.util.regex.Pattern;

/**
 * Listens for clan chat coffer deposit messages and plays a sound effect.
 *
 * <p>Matches messages of the form:
 * "{RSN} has deposited {amount} coins into the coffer."
 *
 * <p>Sound playback delegates to {@link AudioPlayer} (RuneLite's approved audio API).
 */
@Slf4j
public class CofferDepositSoundService {

    /**
     * Matches clan chat coffer deposit messages.
     * Examples:
     * "Zezima has deposited 1,000,000 coins into the coffer."
     * "Some Player has deposited 500 coins into the coffer."
     */
    private static final Pattern COFFER_DEPOSIT_PATTERN = Pattern.compile(
            ".+ has deposited [\\d,]+ coins into the coffer\\.",
            Pattern.CASE_INSENSITIVE
    );

    @Inject
    private AudioPlayer audioPlayer;

    @Inject
    private BoomerangBanditsConfig config;

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!config.cofferDepositSound()) {
            return;
        }

        // Only listen to clan chat messages
        if (event.getType() != ChatMessageType.CLAN_MESSAGE
                && event.getType() != ChatMessageType.FRIENDSCHAT) {
            return;
        }

        String message = event.getMessage();
        if (message == null) {
            return;
        }

        // Strip any HTML tags RuneLite may inject (e.g. <col=...>text</col>)
        String stripped = message.replaceAll("<[^>]*>", "").trim();

        if (COFFER_DEPOSIT_PATTERN.matcher(stripped).matches()) {
            log.debug("Coffer deposit detected: {}", stripped);
            playDepositSound();
        }
    }

    // ======================================================================
    // SOUND PLAYBACK
    // ======================================================================

    /**
     * Plays the bundled coffer-deposit.wav via RuneLite's AudioPlayer.
     */
    private void playDepositSound() {
        try {
            audioPlayer.play(getClass(), "/com/boomerangbandits/coffer-deposit.wav", 0f);
        } catch (Exception e) {
            log.warn("Could not play coffer deposit sound", e);
        }
    }
}
