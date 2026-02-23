package com.boomerangbandits.util;

import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for throttling refresh operations.
 * 
 * <p>Prevents excessive API calls or UI updates by enforcing a minimum
 * time interval between refresh operations. Thread-safe.</p>
 * 
 * <p>Usage example:</p>
 * <pre>
 * private final RefreshThrottler throttler = new RefreshThrottler(30_000); // 30 seconds
 * 
 * public void refresh() {
 *     if (!throttler.shouldRefresh()) {
 *         return; // Too soon, skip refresh
 *     }
 *     
 *     // Perform refresh operation
 *     fetchData();
 *     
 *     throttler.recordRefresh(); // Update last refresh time
 * }
 * 
 * public void forceRefresh() {
 *     throttler.reset(); // Clear throttle
 *     refresh();
 * }
 * </pre>
 */
@Getter
public class RefreshThrottler {

    /**
     * -- GETTER --
     *  Get the configured cooldown period.
     *
     */
    private final long cooldownMs;
    /**
     * -- GETTER --
     *  Get the timestamp of the last refresh.
     *
     */
    private volatile long lastRefreshTime;
    
    /**
     * Create a new refresh throttler with the specified cooldown period.
     * 
     * @param cooldownMs minimum time in milliseconds between refreshes
     * @throws IllegalArgumentException if cooldownMs is negative
     */
    public RefreshThrottler(long cooldownMs) {
        if (cooldownMs < 0) {
            throw new IllegalArgumentException("Cooldown must be non-negative");
        }
        this.cooldownMs = cooldownMs;
        this.lastRefreshTime = 0;
    }
    
    /**
     * Check if enough time has passed since the last refresh.
     * 
     * <p>This method does NOT update the last refresh time. Call
     * {@link #recordRefresh()} after successfully completing the refresh.</p>
     * 
     * @return true if refresh should proceed, false if still in cooldown
     */
    public boolean shouldRefresh() {
        return System.currentTimeMillis() - lastRefreshTime >= cooldownMs;
    }
    
    /**
     * Record that a refresh has occurred.
     * Updates the last refresh time to the current time.
     */
    public void recordRefresh() {
        lastRefreshTime = System.currentTimeMillis();
    }
    
    /**
     * Reset the throttler, allowing an immediate refresh.
     * Useful for "force refresh" operations.
     */
    public void reset() {
        lastRefreshTime = 0;
    }
    
    /**
     * Get the time remaining until the next refresh is allowed.
     * 
     * @return milliseconds until next refresh, or 0 if refresh is allowed now
     */
    public long getTimeUntilNextRefresh() {
        long elapsed = System.currentTimeMillis() - lastRefreshTime;
        long remaining = cooldownMs - elapsed;
        return Math.max(0, remaining);
    }
    
    /**
     * Check if a refresh is currently in cooldown.
     * 
     * @return true if in cooldown (refresh not allowed), false otherwise
     */
    public boolean isInCooldown() {
        return !shouldRefresh();
    }

}
