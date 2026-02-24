package com.boomerangbandits.api;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.models.GroupSyncResponse;
import com.boomerangbandits.api.models.PlayerUpdateResponse;
import com.boomerangbandits.api.models.WomCompetition;
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
 * WiseOldMan API client (via Backend Proxy).
 * Fetches competition data for the Boomerang Bandits clan through the backend API.
 * <p>
 * The backend proxies WOM API calls and provides Redis caching for better performance.
 * Backend handles group ID (11575), caching, rate limiting, and stale data fallback.
 * <p>
 * All HTTP calls use enqueue() (async). Never call execute().
 * <p>
 * Backend API: /api/wom/*
 * Original WOM API docs: <a href="https://docs.wiseoldman.net/">WiseOldMan</a>
 */
@Slf4j
@Singleton
public class WomApiService {

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final BoomerangBanditsConfig config;

    @Inject
    public WomApiService(@Named("boomerang") OkHttpClient httpClient, Gson gson, BoomerangBanditsConfig config) {
        this.httpClient = httpClient;
        this.gson = gson;
        this.config = config;
    }

    /**
     * Fetch all competitions for the group.
     * Backend handles caching and returns fresh data.
     * Uses active_only=true to fetch only currently active competitions.
     */
    public void fetchCompetitions(@Nonnull Consumer<List<WomCompetition>> onSuccess,
                                  @Nonnull Consumer<Exception> onError) {
        String url = ApiConstants.BACKEND_BASE_URL + "/wom/competitions?active_only=true";
        log.debug("Fetching competitions from backend API: {}", url);
        Request request = buildRequest(url);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch competitions from backend", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.warn("Backend competitions returned HTTP {}", response.code());
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    log.debug("Backend competitions response body length: {}", body.length());
                    Type listType = new TypeToken<List<WomCompetition>>() {
                    }.getType();
                    List<WomCompetition> competitions = gson.fromJson(body, listType);
                    log.debug("Parsed {} competitions from backend API", competitions != null ? competitions.size() : 0);
                    onSuccess.accept(competitions);
                } catch (Exception e) {
                    log.error("Failed to parse competitions response", e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Fetch a single competition with full participant data.
     * Backend handles caching and returns fresh data.
     */
    public void fetchCompetitionDetails(int competitionId,
                                        @Nonnull Consumer<WomCompetition> onSuccess,
                                        @Nonnull Consumer<Exception> onError) {
        String url = ApiConstants.BACKEND_BASE_URL + "/wom/competitions/" + competitionId;
        Request request = buildRequest(url);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch competition {} from backend", competitionId, e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    WomCompetition competition = gson.fromJson(body, WomCompetition.class);
                    onSuccess.accept(competition);
                } catch (Exception e) {
                    log.error("Failed to parse competition {} response", competitionId, e);
                    onError.accept(e);
                }
            }
        });
    }

    // ======================================================================
    // WRITE ENDPOINTS (require X-Member-Code, organiser+ rank)
    // ======================================================================

    /**
     * POST /api/wom/players/update
     * <p>
     * Enqueues a background poll for one account. Non-blocking (202 Accepted).
     * Retry on 503 (queue unavailable). Do not retry on 400/401/403.
     *
     * @param username    player's RSN (required)
     * @param accountHash client.getAccountHash() — pass 0 if unavailable
     * @param accountType e.g. "normal", "ironman"
     * @param onSuccess   callback with PlayerUpdateResponse on 202
     * @param onError     callback with exception on failure
     */
    public void updatePlayer(@Nonnull String username, long accountHash,
                             @Nonnull String accountType,
                             @Nonnull Consumer<PlayerUpdateResponse> onSuccess,
                             @Nonnull Consumer<Exception> onError) {
        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            onError.accept(new IllegalStateException("Not authenticated"));
            return;
        }

        String ownerTag = null; // ownerTag removed from config — no longer used
        String json = gson.toJson(new PlayerUpdateRequest(username, accountHash, accountType, ownerTag, false));

        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/wom/players/update")
                .addHeader("X-Member-Code", memberCode)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .post(RequestBody.create(ApiConstants.JSON, json))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to queue player update for {}", username, e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (response.code() == 202) {
                        PlayerUpdateResponse result = gson.fromJson(
                                response.body().string(), PlayerUpdateResponse.class);
                        onSuccess.accept(result);
                    } else {
                        String body = response.body() != null ? response.body().string() : "";
                        onError.accept(new IOException("HTTP " + response.code() + ": " + body));
                    }
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * POST /api/wom/groups/{groupId}/sync
     * <p>
     * Syncs the clan roster to backend member/account state.
     * mode="add_only" upserts listed members without deactivating missing ones.
     * mode="overwrite" treats the payload as source of truth.
     *
     * @param groupId   backend group ID (e.g. 11575)
     * @param mode      "add_only" or "overwrite"
     * @param members   list of members to sync
     * @param onSuccess callback with GroupSyncResponse on 200
     * @param onError   callback with exception on failure
     */
    public void syncGroupMembers(int groupId, @Nonnull String mode,
                                 @Nonnull List<SyncMember> members,
                                 @Nonnull Consumer<GroupSyncResponse> onSuccess,
                                 @Nonnull Consumer<Exception> onError) {
        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            onError.accept(new IllegalStateException("Not authenticated"));
            return;
        }

        String json = gson.toJson(new GroupSyncRequest(mode, members));

        Request request = new Request.Builder()
                .url(ApiConstants.BACKEND_BASE_URL + "/wom/groups/" + groupId + "/sync")
                .addHeader("X-Member-Code", memberCode)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .post(RequestBody.create(ApiConstants.JSON, json))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to sync group {} members", groupId, e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (response.code() == 403) {
                            onError.accept(new SecurityException("Insufficient rank to sync group members"));
                        } else if (response.code() == 404) {
                            onError.accept(new IllegalArgumentException("Group ID " + groupId + " not found on backend"));
                        } else {
                            onError.accept(new IOException("HTTP " + response.code() + ": " + body));
                        }
                        return;
                    }
                    GroupSyncResponse result = gson.fromJson(
                            response.body().string(), GroupSyncResponse.class);
                    if (result.getData() != null && result.getData().getInvalid() > 0) {
                        log.warn("Group sync partial: {} invalid rows", result.getData().getInvalid());
                    }
                    onSuccess.accept(result);
                } catch (Exception e) {
                    onError.accept(e);
                }
            }
        });
    }

    // ======================================================================
    // REQUEST DTOs (write endpoints)
    // ======================================================================

    @Nonnull
    private Request buildRequest(@Nonnull String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
    }

    /**
     * Request body for POST /api/wom/players/update
     */
    public static class PlayerUpdateRequest {
        private final String username;
        private final long accountHash;
        private final String accountType;
        private final String ownerTag;
        private final boolean force;

        public PlayerUpdateRequest(String username, long accountHash, String accountType,
                                   String ownerTag, boolean force) {
            this.username = username;
            this.accountHash = accountHash;
            this.accountType = accountType;
            this.ownerTag = ownerTag;
            this.force = force;
        }
    }

    /**
     * Request body for POST /api/wom/groups/{groupId}/sync
     */
    public static class GroupSyncRequest {
        private final String mode;
        private final List<SyncMember> members;

        public GroupSyncRequest(String mode, List<SyncMember> members) {
            this.mode = mode;
            this.members = members;
        }
    }

    /**
     * A single member entry in a group sync request
     */
    public static class SyncMember {
        private final String username;
        private final String role;
        private final String accountType;
        private final String ownerTag;

        public SyncMember(String username, String role, String accountType, String ownerTag) {
            this.username = username;
            this.role = role;
            this.accountType = accountType;
            this.ownerTag = ownerTag;
        }
    }
}
