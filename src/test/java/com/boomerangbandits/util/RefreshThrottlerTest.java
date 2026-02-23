package com.boomerangbandits.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for RefreshThrottler.
 */
public class RefreshThrottlerTest {
    
    private RefreshThrottler throttler;
    
    @Before
    public void setUp() {
        throttler = new RefreshThrottler(1000); // 1 second cooldown
    }
    
    @Test
    public void testConstructor_ValidCooldown() {
        RefreshThrottler t = new RefreshThrottler(5000);
        assertEquals(5000, t.getCooldownMs());
        assertEquals(0, t.getLastRefreshTime());
    }
    
    @Test
    public void testConstructor_ZeroCooldown() {
        RefreshThrottler t = new RefreshThrottler(0);
        assertEquals(0, t.getCooldownMs());
        assertTrue(t.shouldRefresh()); // Always allows refresh
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NegativeCooldown() {
        new RefreshThrottler(-1000);
    }
    
    @Test
    public void testShouldRefresh_InitialState() {
        assertTrue("Should allow refresh on first call", throttler.shouldRefresh());
    }
    
    @Test
    public void testShouldRefresh_AfterRecording() {
        throttler.recordRefresh();
        assertFalse("Should not allow refresh immediately after recording", throttler.shouldRefresh());
    }
    
    @Test
    public void testShouldRefresh_AfterCooldown() throws InterruptedException {
        throttler.recordRefresh();
        Thread.sleep(1100); // Wait longer than cooldown
        assertTrue("Should allow refresh after cooldown expires", throttler.shouldRefresh());
    }
    
    @Test
    public void testRecordRefresh_UpdatesTimestamp() {
        long before = System.currentTimeMillis();
        throttler.recordRefresh();
        long after = System.currentTimeMillis();
        
        long recorded = throttler.getLastRefreshTime();
        assertTrue("Recorded time should be >= before", recorded >= before);
        assertTrue("Recorded time should be <= after", recorded <= after);
    }
    
    @Test
    public void testReset_AllowsImmediateRefresh() {
        throttler.recordRefresh();
        assertFalse("Should be in cooldown", throttler.shouldRefresh());
        
        throttler.reset();
        assertTrue("Should allow refresh after reset", throttler.shouldRefresh());
        assertEquals("Last refresh time should be 0", 0, throttler.getLastRefreshTime());
    }
    
    @Test
    public void testIsInCooldown() {
        assertFalse("Should not be in cooldown initially", throttler.isInCooldown());
        
        throttler.recordRefresh();
        assertTrue("Should be in cooldown after recording", throttler.isInCooldown());
    }
    
    @Test
    public void testGetTimeUntilNextRefresh_InitialState() {
        assertEquals("Should be 0 initially", 0, throttler.getTimeUntilNextRefresh());
    }
    
    @Test
    public void testGetTimeUntilNextRefresh_AfterRecording() {
        throttler.recordRefresh();
        long remaining = throttler.getTimeUntilNextRefresh();
        
        assertTrue("Remaining time should be > 0", remaining > 0);
        assertTrue("Remaining time should be <= cooldown", remaining <= throttler.getCooldownMs());
    }
    
    @Test
    public void testGetTimeUntilNextRefresh_AfterCooldown() throws InterruptedException {
        throttler.recordRefresh();
        Thread.sleep(1100);
        
        assertEquals("Should be 0 after cooldown", 0, throttler.getTimeUntilNextRefresh());
    }
    
    @Test
    public void testMultipleRefreshCycles() throws InterruptedException {
        // First refresh
        assertTrue(throttler.shouldRefresh());
        throttler.recordRefresh();
        assertFalse(throttler.shouldRefresh());
        
        // Wait for cooldown
        Thread.sleep(1100);
        
        // Second refresh
        assertTrue(throttler.shouldRefresh());
        throttler.recordRefresh();
        assertFalse(throttler.shouldRefresh());
    }
    
    @Test
    public void testForceRefreshPattern() {
        // Normal refresh
        throttler.recordRefresh();
        assertFalse("Should be in cooldown", throttler.shouldRefresh());
        
        // Force refresh by resetting
        throttler.reset();
        assertTrue("Should allow refresh after reset", throttler.shouldRefresh());
        throttler.recordRefresh();
        assertFalse("Should be in cooldown again", throttler.shouldRefresh());
    }
    
    @Test
    public void testThreadSafety_ConcurrentAccess() throws InterruptedException {
        final int threadCount = 10;
        final Thread[] threads = new Thread[threadCount];
        final boolean[] results = new boolean[threadCount];
        
        // All threads try to refresh at the same time
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = throttler.shouldRefresh();
                if (results[index]) {
                    throttler.recordRefresh();
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join();
        }
        
        // At least one thread should have been allowed to refresh
        boolean anyAllowed = false;
        for (boolean result : results) {
            if (result) {
                anyAllowed = true;
                break;
            }
        }
        assertTrue("At least one thread should be allowed to refresh", anyAllowed);
    }
    
    @Test
    public void testZeroCooldown_AlwaysAllowsRefresh() {
        RefreshThrottler noThrottle = new RefreshThrottler(0);
        
        assertTrue(noThrottle.shouldRefresh());
        noThrottle.recordRefresh();
        assertTrue("Should still allow refresh with 0 cooldown", noThrottle.shouldRefresh());
        noThrottle.recordRefresh();
        assertTrue("Should still allow refresh with 0 cooldown", noThrottle.shouldRefresh());
    }
    
    @Test
    public void testLongCooldown() {
        RefreshThrottler longThrottle = new RefreshThrottler(60_000); // 1 minute
        
        assertTrue(longThrottle.shouldRefresh());
        longThrottle.recordRefresh();
        assertFalse(longThrottle.shouldRefresh());
        
        long remaining = longThrottle.getTimeUntilNextRefresh();
        assertTrue("Remaining should be close to 60 seconds", remaining > 59_000 && remaining <= 60_000);
    }
}
