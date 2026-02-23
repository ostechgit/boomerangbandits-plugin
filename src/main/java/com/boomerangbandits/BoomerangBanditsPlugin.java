package com.boomerangbandits;

import com.boomerangbandits.api.AdminApiService;
import com.boomerangbandits.api.AuthHeaderInterceptor;
import com.boomerangbandits.api.ClanApiService;
import com.google.inject.Binder;
import com.boomerangbandits.api.ClanContentService;
import com.boomerangbandits.api.WomApiService;
import com.boomerangbandits.api.WomApiService.SyncMember;
import java.util.ArrayList;
import net.runelite.api.clan.ClanSettings;
import net.runelite.client.util.Text;
import com.boomerangbandits.notifiers.*;
import com.boomerangbandits.services.EventAttendanceTracker;
import com.boomerangbandits.services.CompetitionScheduler;
import com.boomerangbandits.services.ConfigSyncService;
import com.boomerangbandits.services.WebhookService;
import com.boomerangbandits.ui.BoomerangPanel;
import com.boomerangbandits.ui.EventOverlay;
import com.boomerangbandits.ui.panels.ClanHubPanel;
import com.boomerangbandits.util.ClanValidator;
import com.boomerangbandits.util.EventFilterManager;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.ClanMemberJoined;
import net.runelite.api.events.ClanMemberLeft;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.config.ConfigManager;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
    name = "Boomerang Bandits",
    description = "Official plugin for Boomerang Bandits clan members",
    tags = {"clan", "boomerang", "bandits", "events", "tracking", "cc"}
)
@Slf4j
public class BoomerangBanditsPlugin extends Plugin {

    // RuneLite injected dependencies
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ConfigManager configManager;
    @Inject private ClientToolbar clientToolbar;
    @Inject private EventBus eventBus;
    @Inject private ScheduledExecutorService executor;
    @Inject private OverlayManager overlayManager;

    // Plugin components
    @Inject private BoomerangBanditsConfig config;
    @Inject private AuthHeaderInterceptor authInterceptor;
    @Inject private ClanApiService clanApi;
    @Inject private ClanContentService contentService;
    @Inject private AdminApiService adminApi;
    @Inject private ClanValidator clanValidator;
    @Inject private ConfigSyncService configSyncService;

    // Phase 2: Services
    @Inject private WebhookService webhookService;

    // Phase 3: Services
    @Inject private WomApiService womApi;
    @Inject private CompetitionScheduler competitionScheduler;
    @Inject private EventFilterManager eventFilterManager;

    // Clan Rank Sync
    @Inject private com.boomerangbandits.services.ClanRankSyncService clanRankSyncService;

    // Attendance tracking
    @Inject private EventAttendanceTracker attendanceTracker;

    // Sound effects
    @Inject private com.boomerangbandits.services.CofferDepositSoundService cofferDepositSoundService;

    // Notifiers
    @Inject private LoginNotifier loginNotifier;
    @Inject private LogoutNotifier logoutNotifier;

    // Phase 6: Overlay
    @Inject private EventOverlay eventOverlay;

    // UI
    private BoomerangPanel panel;
    private NavigationButton navButton;

    // State
    private volatile boolean authenticated = false;

    @Override
    public void configure(Binder binder) {
        binder.install(new BoomerangBanditsModule());
    }

    @Override
    protected void startUp() {
        panel = injector.getInstance(BoomerangPanel.class);

        BufferedImage icon = ImageUtil.loadImageResource(
            getClass(), "/com/boomerangbandits/boomerang-icon.png"
        );

        navButton = NavigationButton.builder()
            .tooltip("Boomerang Bandits")
            .icon(icon)
            .priority(config.menuPriority())
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        // Wire group sync callback into admin panel
        panel.getAdminPanel().setOnGroupSync(this::triggerGroupSync);
        // Wire attendance tracker into admin panel
        panel.getAdminPanel().setAttendanceTracker(attendanceTracker, adminApi);

        // Register Phase 6 overlay
        overlayManager.add(eventOverlay);

        // Register notifiers
        registerNotifiers();

        log.info("Boomerang Bandits plugin started (devMode: {})", config.devMode());
    }

    @Override
    protected void shutDown() {
        unregisterNotifiers();
        competitionScheduler.stop();
        clanRankSyncService.stop();
        overlayManager.remove(eventOverlay);
        clientToolbar.removeNavigation(navButton);
        configSyncService.stop();
        authenticated = false;

        log.info("Boomerang Bandits plugin stopped");
    }

    // ======================================================================
    // EVENT HANDLERS
    // ======================================================================

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        try {
            switch (event.getGameState()) {
                case LOGGED_IN:
                    // Guard: don't re-auth on world hop or reconnect
                    if (!authenticated) {
                        handleLogin();
                    } else {
                        // Still re-validate clan channel after world hop
                        // (clan channel reloads on hop)
                        executor.schedule(
                            () -> clientThread.invoke(() -> {
                                if (clanValidator.validate()) {
                                    SwingUtilities.invokeLater(() -> panel.updateAdminVisibility());
                                }
                            }),
                            5, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    break;

                case LOGIN_SCREEN:
                case HOPPING:
                    attendanceTracker.onHoppingOrLogin();
                    if (authenticated) {
                        handleLogout();
                    }
                    break;

                case CONNECTION_LOST:
                    // Don't logout — player may reconnect
                    // But pause schedulers to avoid wasted requests
                    log.info("Connection lost, pausing schedulers");
                    competitionScheduler.stop();
                    configSyncService.stop();
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling GameStateChanged", e);
        }
    }

    @Subscribe
    public void onClanChannelChanged(ClanChannelChanged event) {
        try {
            if (client.getGameState() == GameState.LOGGED_IN && !authenticated) {
                // Reset validator so a previous failed attempt doesn't block us
                clanValidator.reset();
                log.debug("Clan channel changed, attempting authentication in 2s");
                executor.schedule(
                    () -> clientThread.invoke(this::attemptAuthentication),
                    2, java.util.concurrent.TimeUnit.SECONDS
                );
            } else if (authenticated) {
                // Re-validate — player may have left the CC
                executor.schedule(
                    () -> clientThread.invoke(() -> {
                        if (!clanValidator.validate()) {
                            log.info("Player left clan channel — locking panel");
                            handleLogout();
                        } else {
                            SwingUtilities.invokeLater(() -> panel.updateAdminVisibility());
                        }
                    }),
                    2, java.util.concurrent.TimeUnit.SECONDS
                );
            }
        } catch (Exception e) {
            log.error("Error handling ClanChannelChanged", e);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        try {
            if (!event.getGroup().equals(BoomerangBanditsConfig.GROUP)) {
                return;
            }

            // If the member code was just entered/changed while logged in, re-attempt auth immediately
            // so the player doesn't have to relog.
            if (event.getKey().equals("memberCode")
                    && client.getGameState() == GameState.LOGGED_IN
                    && !authenticated) {
                log.info("[Auth] Member code changed — re-attempting authentication");
                executor.submit(() -> clientThread.invoke(this::attemptAuthentication));
            }

            SwingUtilities.invokeLater(() -> {
                panel.refreshConfig();
                // Update announcement if changed
                panel.getHomePanel().updateAnnouncement(config.announcementMessage());
                
                // Update admin visibility if admin rank threshold changed
                if (event.getKey().equals("adminRankThreshold")) {
                    panel.updateAdminVisibility();
                }
            });
        } catch (Exception e) {
            log.error("Error handling ConfigChanged", e);
        }
    }

    @Subscribe
    public void onCommandExecuted(CommandExecuted event) {
        try {
            String command = event.getCommand().toLowerCase();
            
            switch (command) {
                case "syncranks":
                    if (!clanValidator.isAdmin()) {
                        log.debug("::syncranks blocked — player does not have admin rank");
                        return;
                    }
                    log.info("Manually syncing clan ranks to backend...");
                    clanRankSyncService.syncRanksManually(
                        response -> {
                            if (response.getInvalid() > 0 || 
                                (response.getErrors() != null && !response.getErrors().isEmpty())) {
                                log.warn("✅ Rank sync completed with {} errors", response.getInvalid());
                            } else {
                                log.info("✅ Rank sync completed successfully");
                            }
                        },
                        error -> log.error("❌ Rank sync failed", error)
                    );
                    break;

                case "syncbandits":
                    if (!clanValidator.isAdmin()) {
                        log.debug("::syncbandits blocked — player does not have admin rank");
                        return;
                    }
                    log.info("Manual group sync triggered via ::syncbandits");
                    triggerGroupSync();
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling command", e);
        }
    }

    // ======================================================================
    // ATTENDANCE TRACKING — forward to EventAttendanceTracker
    // ======================================================================

    @Subscribe
    public void onGameTick(GameTick event) {
        attendanceTracker.onGameTick();
    }

    @Subscribe
    public void onPlayerSpawned(PlayerSpawned event) {
        attendanceTracker.onPlayerSpawned(event.getPlayer());
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned event) {
        attendanceTracker.onPlayerDespawned(event.getPlayer());
    }

    @Subscribe
    public void onClanMemberJoined(ClanMemberJoined event) {
        attendanceTracker.onClanMemberJoined(event.getClanMember());
    }

    @Subscribe
    public void onClanMemberLeft(ClanMemberLeft event) {
        attendanceTracker.onClanMemberLeft(event.getClanMember());
    }

    // ======================================================================
    // LOGIN / LOGOUT / AUTH
    // ======================================================================

    private void handleLogin() {
        // Reset validation state on login
        clanValidator.reset();

        // Wait a moment for clan channel to load, then try authentication on the client thread
        executor.schedule(
            () -> clientThread.invoke(this::attemptAuthentication),
            2, java.util.concurrent.TimeUnit.SECONDS
        );
    }

    private void attemptAuthentication() {
        if (authenticated) {
            return;
        }

        if (!clanValidator.validate()) {
            log.debug("Clan validation failed — features disabled");
            SwingUtilities.invokeLater(() ->
                panel.getHomePanel().updateStatus("Not in clan", ColorScheme.LIGHT_GRAY_COLOR));
            return;
        }

        log.info("Clan validation successful");

        String playerName = client.getLocalPlayer() != null
            ? client.getLocalPlayer().getName()
            : "Unknown";

        SwingUtilities.invokeLater(() -> panel.onLogin(playerName));

        authenticate();
        
        // Notify login after authentication
        loginNotifier.onLogin();
    }

    private void handleLogout() {
        // Notify logout before clearing state
        logoutNotifier.onLogout();
        
        authenticated = false;
        configSyncService.stop();
        clanApi.resetDegradedState();
        clanApi.clearAuthToken();
        authInterceptor.clearCredentials();
        clanValidator.reset(); // Reset clan validation cache
        SwingUtilities.invokeLater(() -> panel.onLogout());
    }

    private void authenticate() {
        if (client.getLocalPlayer() == null) {
            return;
        }

        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            log.warn("[Auth] Skipping authentication — account hash not yet available");
            return;
        }
        String rsn = client.getLocalPlayer().getName();

        // Member code is manually provided by the player (given by a clan admin).
        // If not set yet, still send the verify request — the backend will create
        // a pending member record and notify the admin. The plugin shows a
        // "waiting for member code" status until the admin sends it.
        String memberCode = config.memberCode();
        boolean hasMemberCode = memberCode != null && !memberCode.trim().isEmpty();

        String authToken = null;
        if (hasMemberCode) {
            // Derive auth token deterministically: HMAC-SHA256(key=memberCode, message=accountHash)
            // Reproducible on any install — player just re-enters their member code.
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(memberCode.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] hash = mac.doFinal(String.valueOf(accountHash).getBytes(StandardCharsets.UTF_8));
                authToken = Base64.getEncoder().encodeToString(hash);
            } catch (Exception e) {
                log.error("[Auth] Failed to compute auth token", e);
                return;
            }
            clanApi.setAuthToken(authToken);
        }

        // Set credentials on the interceptor — all services pick them up automatically
        authInterceptor.setCredentials(memberCode, authToken, accountHash);
        // Also keep ClanApiService in sync for its own internal state tracking
        clanApi.setAuthToken(authToken);
        clanApi.setAccountHash(accountHash);

        final String finalAuthToken = authToken;

        assert rsn != null;
        clanApi.verifyMember(accountHash, rsn, finalAuthToken,
            authResponse -> {
                if (authResponse.isSuccess()) {
                    authenticated = true;
                    log.info("Authenticated as: {} (code: {}...)",
                        rsn,
                        authResponse.getMemberCode().substring(0,
                            Math.min(8, authResponse.getMemberCode().length()))
                    );

                    // Start config sync now that we have a member code
                    configSyncService.start(executor);

                    // Start competition scheduler
                    competitionScheduler.start(executor);

                    // Start clan rank sync
                    clanRankSyncService.start(executor);

                    SwingUtilities.invokeLater(() -> {
                        panel.onAuthenticated();
                        panel.getHomePanel().updateAnnouncement(config.announcementMessage());
                        // Update admin visibility after authentication
                        panel.updateAdminVisibility();
                    });

                    // Trigger player update now that we're authenticated — must run on client thread
                    clientThread.invoke(() -> triggerPlayerUpdate(rsn, accountHash));
                } else if (authResponse.isPending()) {
                    // Backend created the member row but hasn't issued a code yet.
                    // Admin will DM the member code — player must enter it in settings.
                    log.info("[Auth] Member registered as pending — awaiting member code from admin");
                    SwingUtilities.invokeLater(() ->
                        panel.getHomePanel().updateStatus(
                            "Registered! Awaiting member code from admin", ColorScheme.LIGHT_GRAY_COLOR));
                } else {
                    log.warn("Authentication failed: {}", authResponse.getError());
                }
            },
            error -> {
                log.warn("Authentication error: {}", error);
                if (clanApi.isDegraded()) {
                    SwingUtilities.invokeLater(() -> panel.onDegraded());
                }
            }
        );
    }

    // ======================================================================
    // WOM WRITE HELPERS
    // ======================================================================

    /**
     * Trigger a player update for the local player.
     * Called on login after auth, and can be called on manual refresh.
     */
    public void triggerPlayerUpdate(String rsn, long accountHash) {
        // Derive account type from the game client varbit rather than config
        int ironmanVarbit = client.getVarbitValue(net.runelite.api.gameval.VarbitID.IRONMAN);
        String accountType;
        if (ironmanVarbit == 1) {
            accountType = "ironman";
        } else if (ironmanVarbit == 2) {
            accountType = "hardcore_ironman";
        } else if (ironmanVarbit == 3) {
            accountType = "ultimate_ironman";
        } else if (ironmanVarbit == 4) {
            accountType = "group_ironman";
        } else if (ironmanVarbit == 5) {
            accountType = "hardcore_group_ironman";
        } else {
            accountType = "normal";
        }
        womApi.updatePlayer(rsn, accountHash, accountType,
            result -> log.info("[WOM] Player update queued for {} (job: {})", rsn, result.getData() != null ? result.getData().getJobId() : "?"),
            error -> log.warn("[WOM] Player update failed for {}: {}", rsn, error.getMessage())
        );
    }

    /**
     * Build SyncMember list from ClanSettings and call syncGroupMembers.
     * MUST be called on the client thread (uses ClanSettings).
     * Dispatches the HTTP call off-thread via executor.
     */
    public void triggerGroupSync() {
        clientThread.invoke(() -> {
            ClanSettings clanSettings = client.getClanSettings();
            if (clanSettings == null) {
                log.warn("[WOM] Cannot sync — clan settings not loaded");
                return;
            }

            java.util.List<net.runelite.api.clan.ClanMember> allMembers = clanSettings.getMembers();
            ArrayList<SyncMember> syncMembers = new ArrayList<>(allMembers.size());

            for (net.runelite.api.clan.ClanMember member : allMembers) {
                String rsn = member.getName();
                if (rsn.startsWith("[#")) continue; // skip bots

                String normalizedRsn = Text.toJagexName(rsn);
                net.runelite.api.clan.ClanTitle title = clanSettings.titleForRank(member.getRank());
                String role = title != null && title.getName() != null
                    ? title.getName().toLowerCase()
                    : "member";

                syncMembers.add(new SyncMember(normalizedRsn, role, "normal", null));
            }

            log.info("[WOM] Syncing {} clan members to backend (add_only)", syncMembers.size());

            womApi.syncGroupMembers(11575, "add_only", syncMembers,
                result -> {
                    if (result.getData() != null) {
                        log.info("[WOM] Group sync done — added:{} updated:{} unchanged:{} invalid:{}",
                            result.getData().getAdded(), result.getData().getUpdated(),
                            result.getData().getUnchanged(), result.getData().getInvalid());
                    }
                },
                error -> log.error("[WOM] Group sync failed: {}", error.getMessage())
            );
        });
    }

    // ======================================================================
    // NOTIFIER REGISTRATION
    // ======================================================================

    private void registerNotifiers() {
        // LoginNotifier and LogoutNotifier are called directly by the plugin, not via EventBus
        eventBus.register(cofferDepositSoundService);
    }

    private void unregisterNotifiers() {
        eventBus.unregister(cofferDepositSoundService);
    }

    // ======================================================================
    // CLAN ROSTER LOGGING
    // ======================================================================

    /**
     * Log clan roster based on config settings.
     * Called when joining clan chat if enabled.
     * 
     * IMPORTANT: Must be called on the client thread (use clientThread.invoke())
     * because ClanSettings.titleForRank() requires client thread access.
     */
    @Provides
    BoomerangBanditsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BoomerangBanditsConfig.class);
    }
}
