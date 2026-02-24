package com.boomerangbandits.ui.panels;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.WomApiService;
import com.boomerangbandits.api.models.DailyXpResponse;
import com.boomerangbandits.api.models.PlayerChallenge;
import com.boomerangbandits.api.models.PlayerProfile;
import com.boomerangbandits.api.models.WomCompetition;
import com.boomerangbandits.ui.UIConstants;
import com.boomerangbandits.ui.components.AntialiasedLabel;
import com.boomerangbandits.ui.components.Badge;
import com.boomerangbandits.ui.components.CountdownLabel;
import com.boomerangbandits.util.RefreshThrottler;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;

/**
 * Home/overview panel — the default panel shown when opening the plugin sidebar.
 *
 * Displays:
 *   - Player greeting
 *   - Connection status
 *   - Announcement (from remote config, if any)
 *   - Active competition summary (from WOM cache)
 */
@Slf4j
public class HomePanel extends JPanel {

    private final Client client;
    private final BoomerangBanditsConfig config;
    private final WomApiService womApi;
    private final com.boomerangbandits.api.ClanApiService clanApi;

    private JLabel greetingLabel;
    private JLabel clanPointsLabel;
    private Badge rankBadge;
    private JLabel statusLabel;
    private JLabel announcementLabel;
    private JPanel competitionSection;
    private CountdownLabel competitionCountdown;
    private JPanel clanActivitySection;
    private JPanel challengeSection;
    private JTextArea challengeText;
    private JLabel challengeStreakLabel;
    private JLabel challengeStatsLabel;

    // Local data storage
    private WomCompetition activeCompetition;

    // Throttle each section independently
    private static final long REFRESH_COOLDOWN_MS = 60 * 60 * 1_000; // 1 hour
    private final RefreshThrottler competitionThrottler  = new RefreshThrottler(REFRESH_COOLDOWN_MS);
    private final RefreshThrottler clanActivityThrottler = new RefreshThrottler(REFRESH_COOLDOWN_MS);
    private final RefreshThrottler profileThrottler      = new RefreshThrottler(30_000); // 30 seconds
    private final RefreshThrottler challengeThrottler    = new RefreshThrottler(REFRESH_COOLDOWN_MS);

    @Inject
    public HomePanel(Client client, BoomerangBanditsConfig config, WomApiService womApi,
                     com.boomerangbandits.api.ClanApiService clanApi) {
        this.client = client;
        this.config = config;
        this.womApi = womApi;
        this.clanApi = clanApi;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(
            UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD
        ));

        buildGreetingSection();
        buildAnnouncementSection();
        buildChallengeSection();
        buildClanActivitySection();
        buildCompetitionSection();
    }

    private void buildGreetingSection() {
        greetingLabel = new JLabel("Welcome, Adventurer!");
        greetingLabel.setForeground(Color.WHITE);
        greetingLabel.setFont(UIConstants.deriveFont(
            greetingLabel.getFont(),
            UIConstants.FONT_SIZE_LARGE,
            UIConstants.FONT_BOLD
        ));
        greetingLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(greetingLabel);

        // Badge + points row
        JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        profileRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        profileRow.setAlignmentX(LEFT_ALIGNMENT);
        profileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        rankBadge = new Badge("--", ColorScheme.MEDIUM_GRAY_COLOR);

        clanPointsLabel = new JLabel("Clan Points: --");
        clanPointsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        clanPointsLabel.setFont(UIConstants.deriveFont(clanPointsLabel.getFont(), UIConstants.FONT_SIZE_NORMAL));
        // Derive badge font from clanPointsLabel — JLabel always has a non-null font at construction
        rankBadge.setFont(UIConstants.deriveFont(clanPointsLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
        profileRow.add(rankBadge);
        profileRow.add(clanPointsLabel);

        add(profileRow);

        statusLabel = new JLabel("Not connected");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(UIConstants.deriveFont(statusLabel.getFont(), UIConstants.FONT_SIZE_NORMAL));
        statusLabel.setBorder(new EmptyBorder(2, 0, UIConstants.PADDING_LARGE, 0));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(statusLabel);
    }

    private void buildAnnouncementSection() {
        announcementLabel = new JLabel();
        announcementLabel.setForeground(new Color(0xFFC107)); // amber
        announcementLabel.setFont(UIConstants.deriveFont(announcementLabel.getFont(), UIConstants.FONT_SIZE_NORMAL));
        announcementLabel.setBorder(new EmptyBorder(0, 0, UIConstants.PADDING_LARGE, 0));
        announcementLabel.setAlignmentX(LEFT_ALIGNMENT);
        announcementLabel.setVisible(false);
        add(announcementLabel);
    }

    private void buildChallengeSection() {
        challengeSection = new JPanel();
        challengeSection.setLayout(new BoxLayout(challengeSection, BoxLayout.Y_AXIS));
        challengeSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        challengeSection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xE65100)), // orange border — stands out
            new EmptyBorder(
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD
            )
        ));
        challengeSection.setAlignmentX(LEFT_ALIGNMENT);
        challengeSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        challengeSection.setVisible(false);

        // Header row: "Daily Challenge" label + streak badge on the right
        JPanel headerRow = new JPanel(new GridBagLayout());
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerRow.setAlignmentX(LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        JLabel header = new AntialiasedLabel("Daily Challenge");
        header.setForeground(new Color(0xFF8C00));
        header.setFont(UIConstants.deriveFont(header.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        headerRow.add(header, c);

        c.gridx = 1; c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        challengeStreakLabel = new AntialiasedLabel("Streak: --");
        challengeStreakLabel.setForeground(new Color(0xFFC107));
        challengeStreakLabel.setFont(UIConstants.deriveFont(challengeStreakLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
        headerRow.add(challengeStreakLabel, c);

        challengeSection.add(headerRow);
        challengeSection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        // Challenge text — JTextArea for wrapping (Rule 15)
        challengeText = new JTextArea("Loading...");
        challengeText.setEditable(false);
        challengeText.setLineWrap(true);
        challengeText.setWrapStyleWord(true);
        challengeText.setColumns(0);
        challengeText.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        challengeText.setForeground(Color.WHITE);
        challengeText.setFont(UIConstants.deriveFont(new JLabel().getFont(), UIConstants.FONT_SIZE_NORMAL));
        challengeText.setBorder(null);
        challengeText.setAlignmentX(LEFT_ALIGNMENT);
        challengeText.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        challengeSection.add(challengeText);

        challengeSection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        // Stats row: completed / received
        challengeStatsLabel = new AntialiasedLabel("Completed: -- / --");
        challengeStatsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        challengeStatsLabel.setFont(UIConstants.deriveFont(challengeStatsLabel.getFont(), UIConstants.FONT_SIZE_SMALL));
        challengeStatsLabel.setAlignmentX(LEFT_ALIGNMENT);
        challengeSection.add(challengeStatsLabel);

        add(challengeSection);
        add(Box.createVerticalStrut(UIConstants.SPACING_ITEM));
    }

    private void buildClanActivitySection() {
        clanActivitySection = new JPanel();
        clanActivitySection.setLayout(new BoxLayout(clanActivitySection, BoxLayout.Y_AXIS));
        clanActivitySection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        clanActivitySection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD
            )
        ));
        clanActivitySection.setAlignmentX(LEFT_ALIGNMENT);
        clanActivitySection.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        clanActivitySection.setVisible(false);

        JLabel header = new AntialiasedLabel("Clan Activity Today");
        header.setForeground(Color.WHITE);
        header.setFont(UIConstants.deriveFont(header.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        header.setAlignmentX(LEFT_ALIGNMENT);
        clanActivitySection.add(header);

        clanActivitySection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        add(clanActivitySection);
        add(Box.createVerticalStrut(UIConstants.SPACING_ITEM));
    }

    private void buildCompetitionSection() {
        competitionSection = new JPanel();
        competitionSection.setLayout(new BoxLayout(competitionSection, BoxLayout.Y_AXIS));
        competitionSection.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        competitionSection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD
            )
        ));
        competitionSection.setAlignmentX(LEFT_ALIGNMENT);
        competitionSection.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 100));
        competitionSection.setVisible(false);

        JLabel header = new JLabel("Active Competition");
        header.setForeground(Color.WHITE);
        header.setFont(UIConstants.deriveFont(header.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        header.setAlignmentX(LEFT_ALIGNMENT);
        competitionSection.add(header);

        competitionCountdown = new CountdownLabel("Ends in: ");
        competitionCountdown.setAlignmentX(LEFT_ALIGNMENT);
        competitionSection.add(competitionCountdown);

        add(competitionSection);
    }

    /**
     * Force-refresh all sections, bypassing throttle cooldowns.
     * Use for explicit config changes or manual refresh triggers.
     */
    public void forceRefreshAll() {
        competitionThrottler.reset();
        clanActivityThrottler.reset();
        profileThrottler.reset();
        challengeThrottler.reset();
        refreshCompetitionSummary();
        refreshDailyXp();
        refreshProfile();
        refreshChallenge();
    }

    /**
     * Refresh all home panel data sections (throttled).
     */
    public void refresh() {
        refreshCompetitionSummary();
        refreshDailyXp();
        refreshProfile();
        refreshChallenge();
    }

    /**
     * Update the greeting with the player's RSN.
     * Call from Swing EDT.
     */
    public void updateGreeting(String rsn) {
        greetingLabel.setText("Welcome, " + rsn + "!");
    }

    /**
     * Update connection status display.
     * @param status "Connected", "Degraded", or "Not authenticated"
     * @param color status colour
     */
    public void updateStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            statusLabel.setForeground(color);
        });
    }

    /**
     * Update announcement from remote config.
     * Pass null or empty to hide.
     */
    public void updateAnnouncement(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message != null && !message.isEmpty()) {
                // Escape HTML special characters before wrapping in <html> tags to prevent
                // injection of arbitrary HTML from backend-supplied announcement content (S1).
                String sanitized = message.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;");
                announcementLabel.setText("<html>" + sanitized + "</html>");
                announcementLabel.setVisible(true);
            } else {
                announcementLabel.setVisible(false);
            }
        });
    }

    /**
     * Refresh the daily challenge section.
     * Fetches the player's current incomplete challenge from the backend.
     */
    public void refreshChallenge() {
        if (!challengeThrottler.shouldRefresh()) {
            log.debug("[HomePanel] refreshChallenge() skipped — still in cooldown");
            return;
        }
        challengeThrottler.recordRefresh();
        clanApi.fetchPlayerChallenge(
            challenge -> SwingUtilities.invokeLater(() -> updateChallengeSection(challenge)),
            error -> {
                log.debug("[HomePanel] Could not fetch player challenge: {}", error);
                SwingUtilities.invokeLater(() -> challengeSection.setVisible(false));
            }
        );
    }

    private void updateChallengeSection(com.boomerangbandits.api.models.PlayerChallenge response) {
        if (response == null || !response.isSuccess()) {
            challengeSection.setVisible(false);
            return;
        }

        com.boomerangbandits.api.models.PlayerChallenge.Challenge challenge = response.getFirst();
        if (challenge == null || challenge.getChallenge() == null) {
            challengeSection.setVisible(false);
            return;
        }

        challengeText.setText(challenge.getChallenge());

        int streak = challenge.getStreak();
        challengeStreakLabel.setText(streak > 1 ? "Streak: " + streak : "");

        challengeStatsLabel.setText(
            "Completed " + challenge.getNumberCompleted() + " of " + challenge.getNumberReceived()
            + (challenge.isRerolledToday() ? "  (rerolled)" : "")
        );

        challengeSection.setVisible(true);
        challengeSection.revalidate();
        challengeSection.repaint();
    }

    /**
     * Refresh competition summary.
     * Fetches active competition from backend and updates UI.
     */
    public void refreshCompetitionSummary() {
        if (!competitionThrottler.shouldRefresh()) {
            log.debug("[HomePanel] refreshCompetitionSummary() skipped — still in cooldown");
            return;
        }
        competitionThrottler.recordRefresh();
        womApi.fetchCompetitions(
            competitions -> {
                // Find active competition
                WomCompetition active = null;
                for (WomCompetition comp : competitions) {
                    if (comp.isOngoing()) {
                        active = comp;
                        break;
                    }
                }
                this.activeCompetition = active;
                
                WomCompetition finalActive = active;
                SwingUtilities.invokeLater(() -> {
                    if (finalActive != null) {
                        competitionSection.setVisible(true);
                        competitionCountdown.setTarget(finalActive.getEndsAt());
                    } else {
                        competitionSection.setVisible(false);
                        competitionCountdown.stop();
                    }
                });
            },
            error -> log.error("Failed to fetch competitions for home panel", error)
        );
    }

    /**
     * Refresh clan activity today section.
     * Single call to /api/stats/clan/daily-xp replaces the old multi-call WOM approach.
     */
    public void refreshDailyXp() {
        if (!clanActivityThrottler.shouldRefresh()) {
            log.debug("[HomePanel] refreshDailyXp() skipped — still in cooldown");
            return;
        }
        clanActivityThrottler.recordRefresh();
        clanApi.fetchDailyXp(
            response -> SwingUtilities.invokeLater(() -> {
                if (response != null && response.isSuccess()) {
                    updateClanActivity(response);
                    clanActivitySection.setVisible(true);
                } else {
                    clanActivitySection.setVisible(false);
                }
            }),
            error -> log.error("Failed to fetch daily XP summary: {}", error)
        );
    }

    /**
     * Fetch the current player's profile (rank + points) and update the greeting row.
     * Throttled to 30 seconds.
     */
    public void refreshProfile() {
        if (!profileThrottler.shouldRefresh()) {
            return;
        }
        profileThrottler.recordRefresh();
        clanApi.fetchPlayerProfile(
            profile -> SwingUtilities.invokeLater(() -> updateProfileRow(profile)),
            error -> log.debug("[HomePanel] Could not fetch player profile for greeting: {}", error)
        );
    }

    private void updateProfileRow(PlayerProfile profile) {
        if (profile.getClanRank() != null) {
            rankBadge.setText(profile.getClanRank());
            rankBadge.setBgColor(getRankColor(profile.getClanRank()));
        }
        if (profile.getPoints() != null) {
            clanPointsLabel.setText("Clan Points: " + String.format("%,d", profile.getPoints().getTotal()));
        }
    }

    private Color getRankColor(String rank) {
        if (rank == null) return ColorScheme.MEDIUM_GRAY_COLOR;
        switch (rank.toLowerCase()) {
            case "owner":        return new Color(0xE91E63);
            case "deputy owner": return new Color(0x9C27B0);
            case "admin":        return new Color(0x4CAF50);
            case "general":      return new Color(0x2196F3);
            case "captain":      return new Color(0x03A9F4);
            case "lieutenant":   return new Color(0x00BCD4);
            case "sergeant":     return new Color(0x009688);
            case "corporal":     return new Color(0x8BC34A);
            case "recruit":      return ColorScheme.LIGHT_GRAY_COLOR;
            default:             return ColorScheme.MEDIUM_GRAY_COLOR;
        }
    }

    private void updateClanActivity(DailyXpResponse data) {
        // Clear existing content (keep header + spacer = 2 components)
        while (clanActivitySection.getComponentCount() > 2) {
            clanActivitySection.remove(2);
        }

        // Active gainer count
        JLabel gainersLabel = new AntialiasedLabel(data.getPlayersWithXpGainToday() + " players gained XP today");
        gainersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gainersLabel.setFont(UIConstants.deriveFont(gainersLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_ITALIC));
        gainersLabel.setAlignmentX(LEFT_ALIGNMENT);
        gainersLabel.setBorder(new EmptyBorder(0, 0, UIConstants.SPACING_SMALL, 0));
        clanActivitySection.add(gainersLabel);

        // Top players
        List<DailyXpResponse.TopPlayer> topPlayers = data.getTopPlayers();
        if (topPlayers != null && !topPlayers.isEmpty()) {
            JLabel topPlayersLabel = new AntialiasedLabel("Top Gainers:");
            topPlayersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            topPlayersLabel.setFont(UIConstants.deriveFont(topPlayersLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
            topPlayersLabel.setAlignmentX(LEFT_ALIGNMENT);
            clanActivitySection.add(topPlayersLabel);

            for (DailyXpResponse.TopPlayer player : topPlayers) {
                addStatRow(player.getRank() + ". " + player.getRsn(), formatXP(player.getGained()), clanActivitySection);
            }
        }

        // Top skills
        List<DailyXpResponse.TopSkill> topSkills = data.getTopSkills();
        if (topSkills != null && !topSkills.isEmpty()) {
            clanActivitySection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

            JLabel topSkillsLabel = new AntialiasedLabel("Top Skills:");
            topSkillsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            topSkillsLabel.setFont(UIConstants.deriveFont(topSkillsLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
            topSkillsLabel.setAlignmentX(LEFT_ALIGNMENT);
            clanActivitySection.add(topSkillsLabel);

            for (DailyXpResponse.TopSkill skill : topSkills) {
                // metric is e.g. "slayer_xp" — strip "_xp" suffix and capitalize
                String displayName = UIConstants.capitalize(skill.getMetric().replaceAll("_xp$", ""));
                addStatRow(skill.getRank() + ". " + displayName, formatXP(skill.getGained()), clanActivitySection);
            }
        }

        clanActivitySection.revalidate();
        clanActivitySection.repaint();
    }

    private void addStatRow(String label, String value, JPanel target) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 4);
        JLabel leftLabel = new AntialiasedLabel(label);
        leftLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        leftLabel.setFont(UIConstants.deriveFont(leftLabel.getFont(), UIConstants.FONT_SIZE_SMALL));
        row.add(leftLabel, c);

        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 0, 0);
        JLabel rightLabel = new AntialiasedLabel(value);
        rightLabel.setForeground(new Color(0x4CAF50));
        rightLabel.setFont(UIConstants.deriveFont(rightLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
        row.add(rightLabel, c);

        target.add(row);
    }

    private String formatXP(long xp) {
        if (xp >= 1_000_000) {
            return String.format("%.1fM", xp / 1_000_000.0);
        } else if (xp >= 1_000) {
            return String.format("%.1fK", xp / 1_000.0);
        } else {
            return String.valueOf(xp);
        }
    }
}
