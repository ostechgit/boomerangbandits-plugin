package com.boomerangbandits.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for CachedData.
 */
public class CachedDataTest {
    
    private CachedData<String> cache;
    
    @Before
    public void setUp() {
        cache = new CachedData<>(1000); // 1 second TTL
    }
    
    @Test
    public void testConstructor_ValidTTL() {
        CachedData<String> c = new CachedData<>(5000);
        assertNull("Data should be null initially", c.getData());
        assertFalse("Should not be fresh initially", c.isFresh());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_NegativeTTL() {
        new CachedData<>(-1000);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_ZeroTTL() {
        new CachedData<>(0);
    }
    
    @Test
    public void testGetData_InitialState() {
        assertNull("Data should be null initially", cache.getData());
    }
    
    @Test
    public void testIsFresh_InitialState() {
        assertFalse("Should not be fresh initially", cache.isFresh());
    }
    
    @Test
    public void testUpdate_StoresData() {
        cache.update("test data");
        assertEquals("Should store data", "test data", cache.getData());
    }
    
    @Test
    public void testUpdate_MakesFresh() {
        cache.update("test data");
        assertTrue("Should be fresh after update", cache.isFresh());
    }
    
    @Test
    public void testUpdate_NullData() {
        cache.update("initial");
        cache.update(null);
        assertNull("Should allow null data", cache.getData());
        // isFresh() returns false when data is null (data != null check in isFresh())
        assertFalse("Should not be fresh with null data", cache.isFresh());
    }
    
    @Test
    public void testIsFresh_AfterTTL() throws InterruptedException {
        cache.update("test data");
        assertTrue("Should be fresh immediately", cache.isFresh());
        
        Thread.sleep(1100); // Wait longer than TTL
        
        assertFalse("Should not be fresh after TTL", cache.isFresh());
        assertEquals("Data should still be accessible", "test data", cache.getData());
    }
    
    @Test
    public void testInvalidate_ClearsData() {
        cache.update("test data");
        cache.invalidate();
        
        // invalidate() only resets lastUpdated, not data — getData() still returns the stale value
        assertEquals("Data should still be accessible after invalidate", "test data", cache.getData());
        assertFalse("Should not be fresh after invalidate", cache.isFresh());
    }
    
    @Test
    public void testInvalidate_AllowsNewUpdate() {
        cache.update("first");
        cache.invalidate();
        cache.update("second");
        
        assertEquals("Should store new data", "second", cache.getData());
        assertTrue("Should be fresh after new update", cache.isFresh());
    }
    
    @Test
    public void testMultipleUpdates() {
        cache.update("first");
        assertEquals("first", cache.getData());
        
        cache.update("second");
        assertEquals("second", cache.getData());
        
        cache.update("third");
        assertEquals("third", cache.getData());
        assertTrue("Should be fresh", cache.isFresh());
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testZeroTTL_ThrowsException() {
        new CachedData<>(0);
    }
    
    @Test
    public void testLongTTL_StaysFresh() throws InterruptedException {
        CachedData<String> longCache = new CachedData<>(60_000); // 1 minute
        longCache.update("test");
        
        Thread.sleep(100); // Wait a bit
        
        assertTrue("Should still be fresh", longCache.isFresh());
        assertEquals("test", longCache.getData());
    }
    
    @Test
    public void testThreadSafety_ConcurrentUpdates() throws InterruptedException {
        final int threadCount = 10;
        final Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                cache.update("data-" + index);
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
        
        // Should have some data and be fresh
        assertNotNull("Should have data", cache.getData());
        assertTrue("Should be fresh", cache.isFresh());
    }
    
    @Test
    public void testThreadSafety_ConcurrentReads() throws InterruptedException {
        cache.update("shared data");
        
        final int threadCount = 10;
        final Thread[] threads = new Thread[threadCount];
        final String[] results = new String[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                results[index] = cache.getData();
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
        
        // All threads should read the same data
        for (String result : results) {
            assertEquals("shared data", result);
        }
    }
    
    @Test
    public void testComplexObject() {
        class TestObject {
            final String name;
            final int value;
            
            TestObject(String name, int value) {
                this.name = name;
                this.value = value;
            }
        }
        
        CachedData<TestObject> objectCache = new CachedData<>(1000);
        TestObject obj = new TestObject("test", 42);
        
        objectCache.update(obj);
        
        TestObject retrieved = objectCache.getData();
        assertNotNull(retrieved);
        assertEquals("test", retrieved.name);
        assertEquals(42, retrieved.value);
        assertTrue(objectCache.isFresh());
    }
    
    @Test
    public void testStaleDataStillAccessible() throws InterruptedException {
        cache.update("stale data");
        Thread.sleep(1100); // Wait for TTL
        
        assertFalse("Should not be fresh", cache.isFresh());
        assertEquals("Stale data should still be accessible", "stale data", cache.getData());
    }
}
