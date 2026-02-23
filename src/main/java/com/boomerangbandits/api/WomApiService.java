package com.boomerangbandits.api;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.models.GroupSyncResponse;
import com.boomerangbandits.api.models.NameChangesResponse;
import com.boomerangbandits.api.models.PlayerUpdateResponse;
import com.boomerangbandits.api.models.WomCompetition;
import com.boomerangbandits.api.models.WomGroupMember;
import com.boomerangbandits.api.models.WomGroupStatistics;
import com.boomerangbandits.api.models.WomRecord;
import com.boomerangbandits.api.models.WomSkillGains;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
                    Type listType = new TypeToken<List<WomCompetition>>() {}.getType();
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

    /**
     * Fetch group daily gains (overall XP gained today by all members).
     * Backend handles caching and returns fresh data.
     * Uses include_zero=false to exclude players with no gains.
     */
    public void fetchGroupStatistics(@Nonnull Consumer<WomGroupStatistics> onSuccess, 
                                      @Nonnull Consumer<Exception> onError) {
        String url = ApiConstants.BACKEND_BASE_URL + "/wom/gains?period=day&metric=overall&limit=" + ApiConstants.DEFAULT_API_LIMIT + "&include_zero=false";
        Request request = buildRequest(url);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch group gains from backend", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.warn("Backend group gains returned HTTP {}", response.code());
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    Type listType = new TypeToken<List<WomGroupStatistics.PlayerGain>>() {}.getType();
                    List<WomGroupStatistics.PlayerGain> gains = gson.fromJson(body, listType);
                    
                    // Calculate total XP gained and active member count
                    long totalGained = 0;
                    for (WomGroupStatistics.PlayerGain gain : gains) {
                        if (gain.getData() != null) {
                            totalGained += gain.getData().getGained();
                        }
                    }
                    
                    WomGroupStatistics stats = new WomGroupStatistics();
                    stats.setGains(gains);
                    stats.setTotalGained(totalGained);
                    stats.setActiveMembers(gains.size());
                    
                    log.debug("Refreshed group statistics: {} members, {} total XP gained", 
                        gains.size(), totalGained);
                    onSuccess.accept(stats);
                } catch (Exception e) {
                    log.error("Failed to parse group gains response", e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Fetch skill-specific gains for the group.
     * Backend handles caching and returns fresh data.
     * Uses include_zero=false to exclude players with no gains.
     * 
     * @param skill The skill to fetch gains for (e.g., "slayer", "woodcutting")
     */
    public void fetchSkillGains(@Nonnull String skill, 
                                 @Nonnull Consumer<WomSkillGains> onSuccess, 
                                 @Nonnull Consumer<Exception> onError) {
        String url = ApiConstants.BACKEND_BASE_URL + "/wom/gains?period=day&metric=" + skill + "&limit=" + ApiConstants.DEFAULT_API_LIMIT + "&include_zero=false";
        Request request = buildRequest(url);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch skill gains for {} from backend", skill, e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.warn("Backend skill gains for {} returned HTTP {}", skill, response.code());
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    Type listType = new TypeToken<List<WomGroupStatistics.PlayerGain>>() {}.getType();
                    List<WomGroupStatistics.PlayerGain> gains = gson.fromJson(body, listType);
                    
                    long totalGained = 0;
                    for (WomGroupStatistics.PlayerGain gain : gains) {
                        if (gain.getData() != null) {
                            totalGained += gain.getData().getGained();
                        }
                    }
                    
                    WomSkillGains skillGains = new WomSkillGains(skill);
                    skillGains.setGains(gains);
                    skillGains.setTotalGained(totalGained);
                    skillGains.setActiveMembers(gains.size());
                    
                    log.debug("Refreshed {} gains: {} members, {} total XP", 
                        skill, gains.size(), totalGained);
                    onSuccess.accept(skillGains);
                } catch (Exception e) {
                    log.error("Failed to parse skill gains for {}", skill, e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Fetch group records (all-time bests).
     * Backend handles caching and returns fresh data.
     */
    public void fetchGroupRecords(@Nonnull Consumer<List<WomRecord>> onSuccess, 
                                   @Nonnull Consumer<Exception> onError) {
        String url = ApiConstants.BACKEND_BASE_URL + "/wom/records?metric=overall&period=day";
        Request request = buildRequest(url);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to fetch group records from backend", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.warn("Backend group records returned HTTP {}", response.code());
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    log.debug("Backend group records response body length: {}", body.length());
                    
                    Type listType = new TypeToken<List<WomRecord>>() {}.getType();
                    List<WomRecord> records = gson.fromJson(body, listType);
                    
                    if (records == null || records.isEmpty()) {
                        log.warn("Backend returned empty records list");
                    }
                    
                    log.debug("Refreshed group records: {} records", records != null ? records.size() : 0);
                    onSuccess.accept(records);
                } catch (Exception e) {
                    log.error("Failed to parse group records response", e);
                    onError.accept(e);
                }
            }
        });
    }

    /**
     * Fetch group member list.
     * Backend handles caching and returns fresh data.
     */
    public void fetchGroupMembers(@Nonnull Consumer<List<WomGroupMember>> onSuccess, 
                                   @Nonnull Consumer<Exception> onError) {
        String url = ApiConstants.BACKEND_BASE_URL + "/wom/members";
        Request request = buildRequest(url);

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@Nonnull Call call, @Nonnull IOException e) {
                log.warn("Failed to fetch group members from backend", e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        log.warn("Backend group members returned HTTP {}", response.code());
                        onError.accept(new IOException("HTTP " + response.code()));
                        return;
                    }

                    String body = response.body().string();
                    log.debug("Backend group members response body length: {}", body.length());
                    
                    // Backend returns the memberships array directly
                    Type listType = new TypeToken<List<WomGroupMember>>() {}.getType();
                    List<WomGroupMember> members = gson.fromJson(body, listType);
                    
                    if (members == null || members.isEmpty()) {
                        log.warn("Backend returned empty memberships list");
                    }
                    
                    log.debug("Refreshed group members: {} members", members != null ? members.size() : 0);
                    onSuccess.accept(members);
                } catch (Exception e) {
                    log.error("Failed to parse group members response", e);
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
     *
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
     *
     * Syncs the clan roster to backend member/account state.
     * mode="add_only" upserts listed members without deactivating missing ones.
     * mode="overwrite" treats the payload as source of truth.
     *
     * @param groupId  backend group ID (e.g. 11575)
     * @param mode     "add_only" or "overwrite"
     * @param members  list of members to sync
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

    /**
     * POST /api/wom/names/bulk
     *
     * Remaps old RSNs to new RSNs to maintain member/account continuity.
     * Partial success: check response.getData().getErrors() for skipped rows.
     *
     * @param changes   list of {oldName, newName} pairs
     * @param onSuccess callback with NameChangesResponse on 200
     * @param onError   callback with exception on failure
     */
    public void submitNameChanges(@Nonnull List<NameChange> changes,
                                   @Nonnull Consumer<NameChangesResponse> onSuccess,
                                   @Nonnull Consumer<Exception> onError) {
        if (changes.isEmpty()) {
            onError.accept(new IllegalArgumentException("Name changes list must not be empty"));
            return;
        }

        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            onError.accept(new IllegalStateException("Not authenticated"));
            return;
        }

        String json = gson.toJson(changes);

        Request request = new Request.Builder()
            .url(ApiConstants.BACKEND_BASE_URL + "/wom/names/bulk")
            .addHeader("X-Member-Code", memberCode)
            .addHeader("User-Agent", ApiConstants.USER_AGENT)
            .post(RequestBody.create(ApiConstants.JSON, json))
            .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.warn("Failed to submit {} name changes", changes.size(), e);
                onError.accept(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (response) {
                    if (!response.isSuccessful()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (response.code() == 403) {
                            onError.accept(new SecurityException("Insufficient rank to submit name changes"));
                        } else {
                            onError.accept(new IOException("HTTP " + response.code() + ": " + body));
                        }
                        return;
                    }
                    NameChangesResponse result = gson.fromJson(
                        response.body().string(), NameChangesResponse.class);
                    if (result.getData() != null && result.getData().getSkipped() > 0) {
                        log.warn("Name changes partial: {} skipped", result.getData().getSkipped());
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

    /** Request body for POST /api/wom/players/update */
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

    /** Request body for POST /api/wom/groups/{groupId}/sync */
    public static class GroupSyncRequest {
        private final String mode;
        private final List<SyncMember> members;

        public GroupSyncRequest(String mode, List<SyncMember> members) {
            this.mode = mode;
            this.members = members;
        }
    }

    /** A single member entry in a group sync request */
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

    /** A single entry in a bulk name-change request */
    public static class NameChange {
        private final String oldName;
        private final String newName;

        public NameChange(String oldName, String newName) {
            this.oldName = oldName;
            this.newName = newName;
        }
    }

    @Nonnull
    private Request buildRequest(@Nonnull String url) {
        return new Request.Builder()
            .url(url)
            .addHeader("User-Agent", ApiConstants.USER_AGENT)
            .build();
    }
}
