package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;

/**
 * Listens for clan chat coffer deposit messages and plays a sound effect.
 *
 * <p>Matches messages of the form:
 * "{RSN} has deposited {amount} coins into the coffer."
 *
 * <p>Sound playback uses {@code javax.sound.sampled} on a daemon thread
 * to avoid blocking the game thread. No external audio file is required —
 * a short coin-like tone is synthesised at runtime.
 */
@Slf4j
public class CofferDepositSoundService {

    /**
     * Matches clan chat coffer deposit messages.
     * Examples:
     *   "Zezima has deposited 1,000,000 coins into the coffer."
     *   "Some Player has deposited 500 coins into the coffer."
     */
    private static final Pattern COFFER_DEPOSIT_PATTERN = Pattern.compile(
        ".+ has deposited [\\d,]+ coins into the coffer\\.",
        Pattern.CASE_INSENSITIVE
    );

    @Inject
    private ScheduledExecutorService executor;

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
    // SOUND SYNTHESIS
    // ======================================================================

    /**
     * Plays the bundled coffer-deposit.wav on a daemon thread.
     * Uses BufferedInputStream so AudioSystem.getAudioInputStream() can mark/reset the stream.
     */
    private void playDepositSound() {
        executor.execute(() -> {
            try (InputStream raw = getClass().getResourceAsStream(
                    "/com/boomerangbandits/coffer-deposit.wav")) {
                if (raw == null) {
                    log.warn("coffer-deposit.wav not found in resources");
                    return;
                }
                AudioInputStream ais = AudioSystem.getAudioInputStream(
                    new BufferedInputStream(raw));
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                clip.start();
                Thread.sleep(clip.getMicrosecondLength() / 1000 + 50);
                clip.close();
            } catch (LineUnavailableException | UnsupportedAudioFileException
                     | IOException | InterruptedException e) {
                log.warn("Could not play coffer deposit sound", e);
            }
        });
    }
}
