package com.boomerangbandits.api;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.models.*;
import com.boomerangbandits.util.CachedData;
import com.boomerangbandits.util.DevModeDataProvider;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class ClanContentService {
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final BoomerangBanditsConfig config;
    
    // Cached data with TTLs
    private final CachedData<ActiveEvent> activeEventCache;

    @Inject
    public ClanContentService(
        @Named("boomerang") OkHttpClient httpClient,
        Gson gson,
        BoomerangBanditsConfig config
    ) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.config = config;

        this.activeEventCache = new CachedData<>(60 * 60 * 1_000L); // 1 hour
    }

    /**
     * Fetch active event for overlay
     */
    public void fetchActiveEvent(
        Consumer<ActiveEvent> onSuccess,
        Consumer<Exception> onError
    ) {
        // Check cache first
        if (activeEventCache.isFresh()) {
            log.debug("Returning cached active event");
            onSuccess.accept(activeEventCache.getData());
            return;
        }
        
        // Dev mode check
        if (config.devMode()) {
            log.debug("Dev mode: returning mock active event");
            ActiveEvent devData = DevModeDataProvider.getActiveEvent();
            activeEventCache.update(devData);
            onSuccess.accept(devData);
            return;
        }
        
        // Make API call
        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/events/active")
            .get()
            .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch active event", e);
                onError.accept(e);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    assert response.body() != null;
                    String body = response.body().string();
                    ActiveEventResponse resp = gson.fromJson(body, ActiveEventResponse.class);
                    
                    if (resp == null || !resp.isSuccess()) {
                        onError.accept(new IOException("Invalid response"));
                        return;
                    }
                    
                    ActiveEvent event = new ActiveEvent();
                    event.setActive(resp.isActive());
                    event.setEvents(resp.getEvents());
                    
                    activeEventCache.update(event);
                    onSuccess.accept(event);
                    
                } catch (Exception e) {
                    log.error("Failed to parse active event", e);
                    onError.accept(e);
                }
            }
        });
    }
    
    /**
     * DELETE /api/events/active/{eventId} — deactivate/delete an active event.
     */
    public void deleteEvent(
        String memberCode,
        String eventId,
        Runnable onSuccess,
        Consumer<Exception> onError
    ) {
        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/events/active/" + eventId)
            .header("X-Member-Code", memberCode)
            .delete()
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to delete event", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }
                    activeEventCache.invalidate();
                    onSuccess.run();
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * POST /api/events/active — create a new active event.
     *
     * @param memberCode authenticated member code (X-Member-Code header)
     * @param payload    JSON string of the event request body
     * @param onSuccess  called on HTTP 2xx
     * @param onError    called with exception on failure
     */
    public void createEvent(
        String memberCode,
        String payload,
        Runnable onSuccess,
        Consumer<Exception> onError
    ) {
        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/events/active")
            .header("X-Member-Code", memberCode)
            .post(okhttp3.RequestBody.create(ApiConstants.JSON, payload))
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to create event", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "(no body)";
                        log.warn("Create event failed HTTP {}: {}", response.code(), errorBody);
                        onError.accept(new IOException("HTTP " + response.code() + ": " + errorBody));
                        return;
                    }
                    activeEventCache.invalidate();
                    onSuccess.run();
                } catch (Exception e) {
                    log.error("Error handling create event response", e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * PATCH /api/events/active/{eventId} — update an existing active event.
     */
    public void patchEvent(
        String memberCode,
        String eventId,
        String payload,
        Runnable onSuccess,
        Consumer<Exception> onError
    ) {
        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/events/active/" + eventId)
            .header("X-Member-Code", memberCode)
            .patch(okhttp3.RequestBody.create(ApiConstants.JSON, payload))
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to patch event", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "(no body)";
                        log.warn("Patch event failed HTTP {}: {}", response.code(), errorBody);
                        onError.accept(new IOException("HTTP " + response.code() + ": " + errorBody));
                        return;
                    }
                    activeEventCache.invalidate();
                    onSuccess.run();
                } catch (Exception e) {
                    log.error("Error handling patch event response", e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Invalidate all caches
     */
    public void invalidateCaches() {
        activeEventCache.invalidate();
        log.debug("All content caches invalidated");
    }
}
