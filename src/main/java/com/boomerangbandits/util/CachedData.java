package com.boomerangbandits.util;

import javax.annotation.Nullable;

/**
 * Generic cache wrapper with TTL.
 * Thread-safe for read after write (volatile fields).
 * <p>
 * Usage:
 * CachedData<List<WomCompetition>> cache = new CachedData<>(5 * 60 * 1000L);
 * if (cache.isFresh()) return cache.getData();
 * // else fetch and call cache.update(newData);
 */
public class CachedData<T> {
    private final long ttlMs;
    @Nullable
    private volatile T data;
    private volatile long lastUpdated;

    public CachedData(long ttlMs) {
        if (ttlMs <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        this.ttlMs = ttlMs;
        this.lastUpdated = 0;
    }

    public boolean isFresh() {
        return data != null && (System.currentTimeMillis() - lastUpdated) < ttlMs;
    }

    @Nullable
    public T getData() {
        return data;
    }

    public void update(@Nullable T newData) {
        this.data = newData;
        this.lastUpdated = System.currentTimeMillis();
    }

    public void invalidate() {
        this.lastUpdated = 0;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - lastUpdated;
    }
}
