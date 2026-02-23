package com.boomerangbandits.services;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.ApiConstants;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends game event payloads to the backend API.
 *
 * Features:
 *   - Always GZIP compresses request body
 *   - Async HTTP (never blocks game thread)
 *   - Shows toast notification with points awarded on success
 *   - In dev mode: logs payload at debug level, simulates success
 *
 * Called by BaseNotifier.sendNotification().
 */
@Slf4j
@Singleton
public class WebhookService {

    @Inject private @Named("boomerang") OkHttpClient httpClient;
    @Inject private Gson gson;
    @Inject private BoomerangBanditsConfig config;
    @Inject private Client client;
    @Inject private ClientThread clientThread;

    /**
     * Send an event payload to POST /api/events.
     *
     * @param payload   the complete event payload (event_type, data, player info)
     * @param onSuccess callback with the raw response body (runs on OkHttp thread)
     * @param onError   callback with error message (runs on OkHttp thread)
     */
    public void send(Map<String, Object> payload,
                     java.util.function.Consumer<String> onSuccess,
                     java.util.function.Consumer<String> onError) {

        String jsonPayload = gson.toJson(payload);

        if (config.devMode()) {
            log.debug("[DEV] Event payload: {}", jsonPayload);
            // Simulate success with mock points
            showPointsToast(10, (String) payload.get("event_type"));
            onSuccess.accept("{\"success\":true,\"points_awarded\":10}");
            return;
        }

        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            onError.accept("Not authenticated — no member code");
            return;
        }

        byte[] compressed;
        try {
            compressed = gzipCompress(jsonPayload);
        } catch (IOException e) {
            log.warn("GZIP compression failed, sending uncompressed");
            // Fallback to uncompressed
            sendUncompressed(jsonPayload, memberCode, onSuccess, onError);
            return;
        }

        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/events")
            .header("X-Member-Code", memberCode)
            .header("Content-Encoding", "gzip")
            .post(RequestBody.create(ApiConstants.JSON, compressed))
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Event send failed: {}", e.getMessage());
                onError.accept(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body() != null ? response.body().string() : "";

                    if (!response.isSuccessful()) {
                        log.warn("Event send HTTP {}: {}", response.code(), body);
                        onError.accept("HTTP " + response.code());
                        return;
                    }

                    // Parse points from response and show toast
                    parseAndShowPoints(body, (String) payload.get("event_type"));
                    onSuccess.accept(body);
                } catch (Exception e) {
                    log.warn("Event response parse error: {}", e.getMessage());
                    onError.accept(e.getMessage());
                }
            }
        });
    }

    private void sendUncompressed(String json, String memberCode,
                                  java.util.function.Consumer<String> onSuccess,
                                  java.util.function.Consumer<String> onError) {
        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/events")
            .header("X-Member-Code", memberCode)
            .post(RequestBody.create(ApiConstants.JSON, json))
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onError.accept(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    String body = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        onSuccess.accept(body);
                    } else {
                        onError.accept("HTTP " + response.code());
                    }
                }
            }
        });
    }

    private byte[] gzipCompress(String data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    private void parseAndShowPoints(String responseBody, String eventType) {
        try {
            JsonObject json = new com.google.gson.JsonParser().parse(responseBody).getAsJsonObject();
            if (json.has("points_awarded")) {
                int points = json.get("points_awarded").getAsInt();
                if (points > 0) {
                    showPointsToast(points, eventType);
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse points from response: {}", e.getMessage());
        }
    }

    /**
     * Show a game chat message with points awarded.
     * Uses GAMEMESSAGE type so it appears in the chat box as a system message.
     * Must be called on client thread via clientThread.invoke().
     */
    private void showPointsToast(int points, String eventType) {
        String message = String.format(
            "<col=00ff00>+%d points</col> <col=808080>(%s)</col>",
            points, formatEventType(eventType)
        );

        // Must add chat message on the client thread
        clientThread.invoke(() -> {
            try {
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, "Boomerang Bandits");
            } catch (Exception e) {
                log.debug("Could not show points toast: {}", e.getMessage());
            }
        });
    }

    private String formatEventType(String eventType) {
        if (eventType == null) return "event";
        // "LOOT" → "Loot", "KILL_COUNT" → "Kill Count"
        return eventType.substring(0, 1).toUpperCase()
            + eventType.substring(1).toLowerCase().replace("_", " ");
    }
}
