package com.boomerangbandits.api;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;

/**
 * Centralized API configuration constants.
 * Single source of truth for all backend URLs and API settings.
 *
 * <p>The backend URL is injected at build time via Gradle resource filtering.
 * Override with: {@code ./gradlew compileJava -PbackendUrl=https://...}</p>
 */
@Slf4j
public final class ApiConstants {

    // ========================================================================
    // BACKEND API (Boomerang Bandits)
    // ========================================================================

    /**
     * Base URL for all Boomerang Bandits backend API calls.
     * Resolved from api.properties at class-load time.
     * Set at build time via -PbackendUrl Gradle property (defaults to production).
     */
    public static final String BACKEND_BASE_URL;

    static {
        String url = "https://boomerangbandits.cc"; // fallback
        try (InputStream is = ApiConstants.class.getResourceAsStream("/com/boomerangbandits/api.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String loaded = props.getProperty("backend.base.url", "").trim();
                if (!loaded.isEmpty()) {
                    url = loaded;
                }
            }
        } catch (IOException e) {
            log.warn("Could not load api.properties, using fallback URL");
        }
        BACKEND_BASE_URL = url;
    }
    
    /**
     * User agent string for all backend API requests.
     * Format: PluginName/Version
     * 
     * <p>Helps backend identify and track plugin requests for analytics and debugging.</p>
     */
    public static final String USER_AGENT = "BoomerangBanditsPlugin/1.0";
    
    // ========================================================================
    // WISE OLD MAN API (Deprecated - now proxied through backend)
    // ========================================================================
    
    /**
     * WOM API is now accessed through the backend proxy at /api/wom/*
     * Backend handles group ID (11575), caching, and rate limiting.
     * 
     * Historical note: Previously used direct WOM API calls.
     * Migrated to backend proxy on 2026-02-16 for better caching and control.
     */
    
    // ========================================================================
    // COMMON
    // ========================================================================

    /**
     * Default result limit for paginated API endpoints (e.g. /wom/gains).
     * Covers the full clan roster with headroom. Update here if the clan grows significantly.
     */
    public static final int DEFAULT_API_LIMIT = 500;

    /**
     * JSON media type for HTTP request bodies.
     * Used by all services that send JSON payloads (POST/PUT requests).
     */
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static constants.
     */
    private ApiConstants() {
        throw new AssertionError("Utility class - do not instantiate");
    }
}
