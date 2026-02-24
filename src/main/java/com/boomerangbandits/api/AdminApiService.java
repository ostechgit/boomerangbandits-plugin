package com.boomerangbandits.api;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.models.AttendanceEntry;
import com.boomerangbandits.api.models.AttendanceResult;
import com.boomerangbandits.api.models.RankChange;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;

/**
 * API service for admin-only endpoints.
 * All requests include X-Member-Code header for authentication.
 * Backend verifies admin rank server-side.
 * <p>
 * Separated from ClanApiService to keep admin concerns isolated.
 */
@Slf4j
@Singleton
public class AdminApiService {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final BoomerangBanditsConfig config;

    @Inject
    public AdminApiService(@Named("boomerang") OkHttpClient httpClient, Gson gson, BoomerangBanditsConfig config) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.config = config;
    }

    // =========================================================================
    // Attendance Ingestion
    // =========================================================================

    /**
     * Submit structured attendance data for backend processing.
     * Backend matches RSNs to members and awards attendance points.
     *
     * @param eventName       optional event name for record-keeping
     * @param durationSeconds total event duration in seconds
     * @param entries         per-member attendance data from EventAttendanceTracker
     */
    public void submitAttendance(@Nonnull String eventName,
                                 int durationSeconds,
                                 @Nonnull java.util.List<AttendanceEntry> entries,
                                 @Nonnull Consumer<AttendanceResult> onSuccess,
                                 @Nonnull Consumer<Exception> onError) {
        String json = gson.toJson(new AttendanceRequest(eventName, durationSeconds, entries));

        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/admin/attendance/ingest")
                .addHeader("X-Member-Code", config.memberCode())
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .post(RequestBody.create(ApiConstants.JSON, json))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to submit attendance", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (response.code() == 403) {
                            onError.accept(new SecurityException("Insufficient rank for admin actions"));
                        } else {
                            onError.accept(new IOException("HTTP " + response.code() + ": " + body));
                        }
                        return;
                    }
                    AttendanceResult result = gson.fromJson(
                            response.body().string(), AttendanceResult.class
                    );
                    onSuccess.accept(result);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    // =========================================================================
    // Rank Changes
    // =========================================================================

    /**
     * Fetch pending rank changes.
     * Backend: GET /api/admin/rank-changes/pending
     */
    public void fetchPendingRankChanges(@Nonnull Consumer<List<RankChange>> onSuccess,
                                        @Nonnull Consumer<Exception> onError) {
        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/admin/rank-changes/pending")
                .addHeader("X-Member-Code", config.memberCode())
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch rank changes", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, onError);
                        return;
                    }
                    Type listType = new TypeToken<RankChangesResponse>() {
                    }.getType();
                    assert response.body() != null;
                    RankChangesResponse result = gson.fromJson(
                            response.body().string(), listType
                    );
                    onSuccess.accept(result.rankChanges);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Propose a new rank change for a member.
     * Backend: POST /api/admin/rank-changes
     * Immediately updates website rank and queues a pending item for in-game processing.
     */
    public void proposeRankChange(@Nonnull String memberRsn,
                                  @Nonnull String newRank,
                                  @Nonnull String reason,
                                  @Nonnull Consumer<RankChange> onSuccess,
                                  @Nonnull Consumer<Exception> onError) {
        String json = gson.toJson(new ProposeRankChangeRequest(memberRsn, newRank, reason));

        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/admin/rank-changes")
                .addHeader("X-Member-Code", config.memberCode())
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .post(RequestBody.create(ApiConstants.JSON, json))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to propose rank change", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, onError);
                        return;
                    }
                    ProposeRankChangeResponse result = gson.fromJson(
                            response.body().string(), ProposeRankChangeResponse.class);
                    onSuccess.accept(result.rankChange);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Mark a pending rank change as done in-game.
     * Backend: POST /api/admin/rank-changes/{id}/actualize
     */
    public void actualizeRankChange(@Nonnull String rankChangeId,
                                    @Nonnull Consumer<Boolean> onSuccess,
                                    @Nonnull Consumer<Exception> onError) {
        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/admin/rank-changes/" + rankChangeId + "/actualize")
                .addHeader("X-Member-Code", config.memberCode())
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .post(RequestBody.create(ApiConstants.JSON, "{}"))
                .build();

        executeSimplePost(request, onSuccess, onError);
    }

    // =========================================================================
    // Announcements
    // =========================================================================

    /**
     * Update the announcement message.
     * Backend: POST /api/admin/announcement
     */
    public void updateAnnouncement(@Nonnull String message,
                                   @Nonnull Consumer<Boolean> onSuccess,
                                   @Nonnull Consumer<Exception> onError) {
        String json = gson.toJson(new AnnouncementRequest(message));

        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/admin/announcement")
                .addHeader("X-Member-Code", config.memberCode())
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .post(RequestBody.create(ApiConstants.JSON, json))
                .build();

        executeSimplePost(request, onSuccess, onError);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void executeSimplePost(Request request,
                                   Consumer<Boolean> onSuccess,
                                   Consumer<Exception> onError) {
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Admin request failed: {}", request.url(), e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, onError);
                        return;
                    }
                    onSuccess.accept(true);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    private void handleErrorResponse(Response response, Consumer<Exception> onError) throws IOException {
        String body = response.body() != null ? response.body().string() : "";
        if (response.code() == 403) {
            onError.accept(new SecurityException("Insufficient rank for admin actions"));
        } else {
            onError.accept(new IOException("HTTP " + response.code() + ": " + body));
        }
    }

    // Request DTOs
    private static class AttendanceRequest {
        final String eventName;
        final int durationSeconds;
        final java.util.List<AttendanceEntry> entries;

        AttendanceRequest(String eventName, int durationSeconds, java.util.List<AttendanceEntry> entries) {
            this.eventName = eventName;
            this.durationSeconds = durationSeconds;
            this.entries = entries;
        }
    }

    private static class AnnouncementRequest {
        final String message;

        AnnouncementRequest(String message) {
            this.message = message;
        }
    }

    private static class ProposeRankChangeRequest {
        final String memberRsn;
        final String newRank;
        final String reason;

        ProposeRankChangeRequest(String memberRsn, String newRank, String reason) {
            this.memberRsn = memberRsn;
            this.newRank = newRank;
            this.reason = reason;
        }
    }

    private static class ProposeRankChangeResponse {
        boolean success;
        RankChange rankChange;
    }

    private static class RankChangesResponse {
        List<RankChange> rankChanges;
    }
}
