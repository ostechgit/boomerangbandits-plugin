package com.boomerangbandits.ui.components;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import javax.swing.JLabel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;

/**
 * A label that counts down to a target time.
 * Updates every second via Swing Timer.
 * <p>
 * Usage:
 *   CountdownLabel countdown = new CountdownLabel("Ends in: ");
 *   countdown.setTarget("2025-06-15T12:00:00Z");
 *   // Displays "Ends in: 3d 5h 12m"
 */
public class CountdownLabel extends JLabel {

    private String prefix;
    private Instant targetTime;
    private Timer timer;

    public CountdownLabel(String prefix) {
        this.prefix = prefix;
        setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        setText(prefix + "--");
    }

    /**
     * Set the target time as ISO 8601 string.
     * Starts the countdown timer automatically.
     */
    public void setTarget(String iso8601) {
        try {
            this.targetTime = Instant.parse(iso8601);
            startTimer();
        } catch (DateTimeParseException e) {
            setText(prefix + "invalid");
        }
    }

    /**
     * Set the target time as Instant.
     */
    public void setTarget(Instant target) {
        this.targetTime = target;
        startTimer();
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void startTimer() {
        stop(); // Stop existing timer
        updateDisplay();
        timer = new Timer(1000, e -> updateDisplay());
        timer.start();
    }

    private void updateDisplay() {
        if (targetTime == null) {
            setText(prefix + "--");
            return;
        }

        Duration remaining = Duration.between(Instant.now(), targetTime);
        if (remaining.isNegative()) {
            setText(prefix + "Ended");
            stop();
            return;
        }

        long days = remaining.toDays();
        long hours = remaining.toHours() % 24;
        long minutes = remaining.toMinutes() % 60;

        if (days > 0) {
            setText(prefix + days + "d " + hours + "h " + minutes + "m");
        } else if (hours > 0) {
            setText(prefix + hours + "h " + minutes + "m");
        } else {
            long seconds = remaining.getSeconds() % 60;
            setText(prefix + minutes + "m " + seconds + "s");
        }
    }
}
