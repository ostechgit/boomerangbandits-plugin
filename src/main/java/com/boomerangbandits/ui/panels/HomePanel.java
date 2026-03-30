package com.boomerangbandits.ui.panels;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.WomApiService;
import com.boomerangbandits.api.models.DailyXpResponse;
import com.boomerangbandits.api.models.PlayerProfile;
import com.boomerangbandits.api.models.PluginConfigResponse;
import com.boomerangbandits.api.models.WomCompetition;
import com.boomerangbandits.ui.UIConstants;
import com.boomerangbandits.ui.components.AntialiasedLabel;
import com.boomerangbandits.ui.components.AntialiasedTextArea;
import com.boomerangbandits.ui.components.Badge;
import com.boomerangbandits.ui.components.CountdownLabel;
import com.boomerangbandits.util.RefreshThrottler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Home/overview panel — the default panel shown when opening the plugin sidebar.
 * <p>
 * Displays:
 * - Player greeting
 * - Connection status
 * - Announcements (from remote config, if any)
 * - Active competition summary (from WOM cache)
 */
@Slf4j
public class HomePanel extends JPanel {

    // Throttle each section independently
    private static final long REFRESH_COOLDOWN_MS = 60 * 60 * 1_000; // 1 hour
    private final Client client;
    private final BoomerangBanditsConfig config;
    private final WomApiService womApi;
    private final com.boomerangbandits.api.ClanApiService clanApi;
    private final RefreshThrottler competitionThrottler = new RefreshThrottler(REFRESH_COOLDOWN_MS);
    private final RefreshThrottler clanActivityThrottler = new RefreshThrottler(REFRESH_COOLDOWN_MS);
    private final RefreshThrottler profileThrottler = new RefreshThrottler(30_000); // 30 seconds
    private final RefreshThrottler challengeThrottler = new RefreshThrottler(REFRESH_COOLDOWN_MS);
    private JLabel greetingLabel;
    private JLabel clanPointsLabel;
    private Badge rankBadge;
    private JLabel statusLabel;
    private JPanel announcementSection;
    private JPanel announcementContent;
    private JPanel bountySection;
    private JPanel bountyContent;
    private JPanel competitionSection;
    private CountdownLabel competitionCountdown;
    private JPanel clanActivitySection;
    private JPanel challengeSection;
    private AntialiasedTextArea challengeText;
    private JLabel challengeStreakLabel;
    private JLabel challengeStatsLabel;
    // Local data storage
    private WomCompetition activeCompetition;

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
        buildBountySection();
        buildChallengeSection();
        buildClanActivitySection();
        buildCompetitionSection();
    }

    private void buildGreetingSection() {
        greetingLabel = new AntialiasedLabel("Welcome, Adventurer!");
        greetingLabel.setForeground(Color.WHITE);
        greetingLabel.setFont(FontManager.getRunescapeBoldFont());
        greetingLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(greetingLabel);

        // Badge + points row
        JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        profileRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        profileRow.setAlignmentX(LEFT_ALIGNMENT);
        profileRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        rankBadge = new Badge("--", ColorScheme.MEDIUM_GRAY_COLOR);

        clanPointsLabel = new AntialiasedLabel("Clan Points: --");
        clanPointsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        clanPointsLabel.setFont(FontManager.getRunescapeSmallFont());
        // Derive badge font from clanPointsLabel — JLabel always has a non-null font at construction
        rankBadge.setFont(FontManager.getRunescapeBoldFont());
        profileRow.add(rankBadge);
        profileRow.add(clanPointsLabel);

        add(profileRow);

        statusLabel = new AntialiasedLabel("Not connected");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setBorder(new EmptyBorder(2, 0, UIConstants.PADDING_LARGE, 0));
        statusLabel.setAlignmentX(LEFT_ALIGNMENT);
        add(statusLabel);
    }

    private void buildAnnouncementSection() {
        announcementSection = createStyledSection(new Color(0xFFC107));
        announcementSection.add(createSectionHeader("Announcements", new Color(0xFFC107)));
        announcementSection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        announcementContent = new JPanel();
        announcementContent.setLayout(new BoxLayout(announcementContent, BoxLayout.Y_AXIS));
        announcementContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        announcementContent.setAlignmentX(LEFT_ALIGNMENT);
        announcementSection.add(announcementContent);

        add(announcementSection);
        add(Box.createVerticalStrut(UIConstants.SPACING_ITEM));
    }

    private void buildChallengeSection() {
        challengeSection = createStyledSection(new Color(0xE65100));

        // Header row: "Daily Challenge" label + streak badge on the right
        JLabel header = new AntialiasedLabel("Daily Challenge");
        header.setForeground(new Color(0xFF8C00));
        header.setFont(FontManager.getRunescapeBoldFont());

        challengeStreakLabel = new AntialiasedLabel("Streak: --");
        challengeStreakLabel.setForeground(new Color(0xFFC107));
        challengeStreakLabel.setFont(FontManager.getRunescapeSmallFont());

        JPanel headerRow = UIConstants.createKeyValueRow(header, challengeStreakLabel, ColorScheme.DARKER_GRAY_COLOR);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        challengeSection.add(headerRow);
        challengeSection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        // Challenge text — JTextArea for wrapping
        challengeText = createWrappingTextArea("Loading...", Color.WHITE);
        challengeSection.add(challengeText);

        challengeSection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        // Stats row: completed / received
        challengeStatsLabel = new AntialiasedLabel("Completed: -- / --");
        challengeStatsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        challengeStatsLabel.setFont(FontManager.getRunescapeSmallFont());
        challengeStatsLabel.setAlignmentX(LEFT_ALIGNMENT);
        challengeSection.add(challengeStatsLabel);

        add(challengeSection);
        add(Box.createVerticalStrut(UIConstants.SPACING_ITEM));
    }

    private void buildBountySection() {
        bountySection = createStyledSection(new Color(0xFFD700));
        bountySection.add(createSectionHeader("Active Bounties", new Color(0xFFD700)));
		bountySection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        bountyContent = new JPanel();
        bountyContent.setLayout(new BoxLayout(bountyContent, BoxLayout.Y_AXIS));
        bountyContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bountyContent.setAlignmentX(LEFT_ALIGNMENT);
        bountySection.add(bountyContent);

        add(bountySection);
        add(Box.createVerticalStrut(UIConstants.SPACING_ITEM));
    }

    private void buildClanActivitySection() {
        clanActivitySection = createStyledSection(ColorScheme.MEDIUM_GRAY_COLOR);
        clanActivitySection.add(createSectionHeader("Clan Activity Today", Color.WHITE));
        clanActivitySection.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        add(clanActivitySection);
        add(Box.createVerticalStrut(UIConstants.SPACING_ITEM));
    }

    private void buildCompetitionSection() {
        competitionSection = createStyledSection(ColorScheme.MEDIUM_GRAY_COLOR);
        competitionSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        competitionSection.add(createSectionHeader("Active Competition", Color.WHITE));

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
     *
     * @param status "Connected", "Degraded", or "Not authenticated"
     * @param color  status colour
     */
    public void updateStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            statusLabel.setForeground(color);
        });
    }

    /**
     * Update announcements from remote config.
     * Pass null or empty list to hide the section.
     */
    public void updateAnnouncements(List<String> messages) {
        SwingUtilities.invokeLater(() -> {
            announcementContent.removeAll();

            if (messages == null || messages.isEmpty()) {
                announcementSection.setVisible(false);
                return;
            }

            for (String message : messages) {
                if (message == null || message.trim().isEmpty()) {
                    continue;
                }

                if (announcementContent.getComponentCount() > 0) {
                    announcementContent.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));
                }

                // Wrap each announcement in a card with an amber left-border indicator.
                // Unicode bullets don't render in RuneLite's default font, so we use
                // structural separation (matte border) like other RuneLite plugins.
                JPanel itemPanel = new JPanel(new BorderLayout());
                itemPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                itemPanel.setBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, new Color(0xFFC107)));
                itemPanel.setAlignmentX(LEFT_ALIGNMENT);
                itemPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

                AntialiasedTextArea textArea = createWrappingTextArea(message, new Color(0xFFC107));
                textArea.setBorder(new EmptyBorder(2, UIConstants.PADDING_SMALL, 2, 0));

                itemPanel.add(textArea, BorderLayout.CENTER);
                announcementContent.add(itemPanel);
            }

            boolean hasContent = announcementContent.getComponentCount() > 0;
            announcementSection.setVisible(hasContent);
            announcementSection.revalidate();
            announcementSection.repaint();
        });
    }

    public void updateBountySection(List<PluginConfigResponse.Bounty> bounties) {
        SwingUtilities.invokeLater(() -> {
            bountyContent.removeAll();

            if (bounties == null || bounties.isEmpty()) {
                bountySection.setVisible(false);
                return;
            }

            for (PluginConfigResponse.Bounty bounty : bounties) {
                if (bounty == null) {
                    continue;
                }

                if (bountyContent.getComponentCount() > 0) {
                    bountyContent.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));
                }

                JPanel card = new JPanel();
                card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
                card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(0xFFD700)),
                        new EmptyBorder(2, UIConstants.PADDING_SMALL, 2, 0)
                ));
                card.setAlignmentX(LEFT_ALIGNMENT);
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

                String name = bounty.getName() != null ? bounty.getName() : "Unnamed Bounty";
                JLabel nameLabel = new AntialiasedLabel(name);
                nameLabel.setForeground(Color.WHITE);
                nameLabel.setFont(FontManager.getRunescapeBoldFont());
                nameLabel.setAlignmentX(LEFT_ALIGNMENT);
                card.add(nameLabel);

                String description = bounty.getDescription();
                if (description != null && !description.trim().isEmpty()) {
                    AntialiasedTextArea descriptionArea = createWrappingTextArea(description, ColorScheme.LIGHT_GRAY_COLOR);
                    descriptionArea.setBorder(new EmptyBorder(2, 0, 0, 0));
                    descriptionArea.setAlignmentX(LEFT_ALIGNMENT);
                    card.add(descriptionArea);
                }

                if (bounty.getItems() != null && !bounty.getItems().isEmpty()) {
                    String joinedItems = bounty.getItems().stream()
                            .map(item -> item != null ? item.getName() : null)
                            .filter(itemName -> itemName != null && !itemName.trim().isEmpty())
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("");

                    if (!joinedItems.isEmpty()) {
                        JLabel itemLabel = new AntialiasedLabel(joinedItems);
                        itemLabel.setForeground(new Color(0xA0A0A0));
                        itemLabel.setFont(FontManager.getRunescapeSmallFont());
                        itemLabel.setBorder(new EmptyBorder(2, 0, 0, 0));
                        itemLabel.setAlignmentX(LEFT_ALIGNMENT);
                        card.add(itemLabel);
                    }
                }

                bountyContent.add(card);
            }

            boolean hasContent = bountyContent.getComponentCount() > 0;
            bountySection.setVisible(hasContent);
            bountySection.revalidate();
            bountySection.repaint();
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
            case "owner":
                return new Color(0xE91E63);
            case "deputy owner":
                return new Color(0x9C27B0);
            case "admin":
                return new Color(0x4CAF50);
            case "general":
                return new Color(0x2196F3);
            case "captain":
                return new Color(0x03A9F4);
            case "lieutenant":
                return new Color(0x00BCD4);
            case "sergeant":
                return new Color(0x009688);
            case "corporal":
                return new Color(0x8BC34A);
            case "recruit":
                return ColorScheme.LIGHT_GRAY_COLOR;
            default:
                return ColorScheme.MEDIUM_GRAY_COLOR;
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
        gainersLabel.setFont(FontManager.getRunescapeSmallFont());
        gainersLabel.setAlignmentX(LEFT_ALIGNMENT);
        gainersLabel.setBorder(new EmptyBorder(0, 0, UIConstants.SPACING_SMALL, 0));
        clanActivitySection.add(gainersLabel);

        // Top players
        List<DailyXpResponse.TopPlayer> topPlayers = data.getTopPlayers();
        if (topPlayers != null && !topPlayers.isEmpty()) {
            JLabel topPlayersLabel = new AntialiasedLabel("Top Gainers:");
            topPlayersLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            topPlayersLabel.setFont(FontManager.getRunescapeBoldFont());
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
            topSkillsLabel.setFont(FontManager.getRunescapeBoldFont());
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
        JLabel leftLabel = new AntialiasedLabel(label);
        leftLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        leftLabel.setFont(FontManager.getRunescapeSmallFont());

        JLabel rightLabel = new AntialiasedLabel(value);
        rightLabel.setForeground(new Color(0x4CAF50));
        rightLabel.setFont(FontManager.getRunescapeBoldFont());

        target.add(UIConstants.createKeyValueRow(leftLabel, rightLabel, ColorScheme.DARKER_GRAY_COLOR));
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

    // ======================================================================
    // UI HELPERS
    // ======================================================================

    /**
     * Create a styled section panel with border, background, and standard layout.
     */
    private JPanel createStyledSection(Color borderColor) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor),
                new EmptyBorder(
                        UIConstants.PADDING_STANDARD,
                        UIConstants.PADDING_STANDARD,
                        UIConstants.PADDING_STANDARD,
                        UIConstants.PADDING_STANDARD
                )
        ));
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        section.setVisible(false);
        return section;
    }

    /**
     * Create a bold header label for a section.
     */
    private JLabel createSectionHeader(String text, Color color) {
        JLabel header = new AntialiasedLabel(text);
        header.setForeground(color);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setAlignmentX(LEFT_ALIGNMENT);
        return header;
    }

    /**
     * Create a read-only wrapping text area for section content.
     */
    private AntialiasedTextArea createWrappingTextArea(String text, Color foreground) {
        AntialiasedTextArea area = new AntialiasedTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setColumns(0);
        area.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        area.setForeground(foreground);
        area.setFont(FontManager.getRunescapeSmallFont());
        area.setBorder(null);
        area.setAlignmentX(LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return area;
    }
}
