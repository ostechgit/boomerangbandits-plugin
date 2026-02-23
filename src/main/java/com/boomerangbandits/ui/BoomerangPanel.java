package com.boomerangbandits.ui;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.ui.UIConstants;
import com.boomerangbandits.ui.components.NavButton;
import com.boomerangbandits.ui.components.PanelFooter;
import com.boomerangbandits.ui.panels.AdminPanel;
import com.boomerangbandits.ui.panels.BasePanel;
import com.boomerangbandits.ui.panels.ClanHubPanel;
import com.boomerangbandits.ui.panels.CompetitionPanel;
import com.boomerangbandits.ui.panels.HomePanel;
import com.boomerangbandits.ui.panels.LeaderboardPanel;
import com.boomerangbandits.util.ClanValidator;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Main side panel with icon navigation bar and CardLayout content.
 * <p>
 * Navigation bar: horizontal row of icon buttons (Home, Profile, Leaderboard, Competition, Info)
 * Content: CardLayout switching between sub-panels
 * <p>
 * Phase 1 had a simple greeting panel. This Phase 3 rewrite replaces it entirely.
 */
@Slf4j
public class BoomerangPanel extends PluginPanel {

    // Card identifiers
    public static final String CARD_HOME = "HOME";
    public static final String CARD_LEADERBOARD = "LEADERBOARD";
    public static final String CARD_COMPETITION = "COMPETITION";
    public static final String CARD_HUB = "HUB";
    public static final String CARD_ADMIN = "ADMIN";
    public static final String CARD_LOCKED = "LOCKED";

    /**
     * -- GETTER --
     *  Get the home panel for direct updates (greeting, status, announcement).
     */
    @Getter
    private final HomePanel homePanel;
    private final LeaderboardPanel leaderboardPanel;
    private final CompetitionPanel competitionPanel;
    private final ClanHubPanel hubPanel;
    /**
     * -- GETTER --
     *  Get the admin panel for direct updates.
     */
    @Getter
    private final AdminPanel adminPanel;
    private final ClanValidator clanValidator;
    private final BoomerangBanditsConfig config;
    private final ClientThread clientThread;

    private CardLayout cardLayout;
    private JPanel contentPanel;
    private JPanel navBar;
    private List<NavButton> navButtons = new ArrayList<>();
    private JButton adminButton;
    /**
     * -- GETTER --
     *  Get the active card identifier.
     */
    @Getter
    private String activeCard = CARD_HOME;
    private PanelFooter stickyFooter;

    @Inject
    public BoomerangPanel(
            HomePanel homePanel,
            LeaderboardPanel leaderboardPanel,
            CompetitionPanel competitionPanel,
            ClanHubPanel hubPanel,
            AdminPanel adminPanel,
            ClanValidator clanValidator,
            BoomerangBanditsConfig config,
            ClientThread clientThread) {
        super(false);

        this.homePanel = homePanel;
        this.leaderboardPanel = leaderboardPanel;
        this.competitionPanel = competitionPanel;
        this.hubPanel = hubPanel;
        this.adminPanel = adminPanel;
        this.clanValidator = clanValidator;
        this.config = config;
        this.clientThread = clientThread;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildNavigationBar();
        buildContentArea();

        stickyFooter = new PanelFooter(this::forceRefreshActivePanel);
        add(stickyFooter, BorderLayout.SOUTH);

        // Start locked — show panels only after clan validation passes
        showLocked();
    }

    private void buildNavigationBar() {
        navBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        navBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        navBar.setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR));
        navBar.setPreferredSize(new Dimension(0, 40));

        // Load icons from resources
        // Icons should be 20x20 PNG files in resources/com/boomerangbandits/
        NavButton homeBtn = createNavButton("home-icon.png", "Home", CARD_HOME);
        NavButton leaderboardBtn = createNavButton("leaderboard-icon.png", "Leaderboard", CARD_LEADERBOARD);
        NavButton competitionBtn = createNavButton("competition-icon.png", "Competitions", CARD_COMPETITION);
        NavButton hubBtn = createNavButton("info-icon.png", "Clan Hub", CARD_HUB);

        navBar.add(homeBtn);
        navBar.add(leaderboardBtn);
        navBar.add(competitionBtn);
        navBar.add(hubBtn);

        // Admin gear icon — right-aligned, only visible to admins
        adminButton = new JButton();
        try {
            adminButton.setIcon(new ImageIcon(ImageUtil.loadImageResource(
                getClass(), "/com/boomerangbandits/profile-icon.png")));
        } catch (Exception e) {
            adminButton.setText("⚙");
        }
        adminButton.setToolTipText("Admin");
        adminButton.setPreferredSize(new Dimension(28, 28));
        adminButton.setBorder(new EmptyBorder(4, 4, 4, 4));
        adminButton.setContentAreaFilled(false);
        adminButton.setFocusPainted(false);
        adminButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        adminButton.setVisible(false); // hidden until admin check passes
        adminButton.addActionListener(e -> showCard(CARD_ADMIN));

        navBar.add(adminButton);

        add(navBar, BorderLayout.NORTH);
    }

    private NavButton createNavButton(String iconFile, String tooltip, String card) {
        ImageIcon icon;
        try {
            icon = new ImageIcon(ImageUtil.loadImageResource(getClass(),
                "/com/boomerangbandits/" + iconFile));
        } catch (Exception e) {
            // Fallback: use first letter as text if icon missing
            log.warn("Icon not found: {}, using placeholder", iconFile);
            icon = null;
        }

        NavButton btn = new NavButton(icon, tooltip, () -> showCard(card));
        navButtons.add(btn);
        return btn;
    }

    private void buildContentArea() {
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        contentPanel.add(buildLockedPanel(), CARD_LOCKED);
        contentPanel.add(scrollWrap(homePanel), CARD_HOME);
        contentPanel.add(scrollWrap(leaderboardPanel), CARD_LEADERBOARD);
        contentPanel.add(scrollWrap(competitionPanel), CARD_COMPETITION);
        contentPanel.add(scrollWrap(hubPanel), CARD_HUB);
        contentPanel.add(scrollWrap(adminPanel), CARD_ADMIN);

        add(contentPanel, BorderLayout.CENTER);
    }

    /** Builds the locked/unauthenticated state panel. */
    private JPanel buildLockedPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setBorder(new EmptyBorder(UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD));

        panel.add(Box.createVerticalGlue());

        JLabel icon = new JLabel("BB");
        icon.setForeground(new Color(0xC8AA6E)); // RS gold
        icon.setFont(UIConstants.deriveFont(icon.getFont(), UIConstants.FONT_SIZE_DISPLAY, UIConstants.FONT_BOLD));
        icon.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(icon);

        panel.add(Box.createVerticalStrut(16));

        JLabel line1 = new JLabel("Set your Member Code");
        line1.setForeground(Color.WHITE);
        line1.setFont(UIConstants.deriveFont(line1.getFont(), UIConstants.FONT_SIZE_LARGE, UIConstants.FONT_BOLD));
        line1.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(line1);

        panel.add(Box.createVerticalStrut(8));
        
        JLabel line2 = new JLabel("Login and join CC");
        line2.setForeground(Color.WHITE);
        line2.setFont(UIConstants.deriveFont(line2.getFont(), UIConstants.FONT_SIZE_LARGE, UIConstants.FONT_BOLD));
        line2.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(line2);

        panel.add(Box.createVerticalStrut(8));

        JLabel line3 = new JLabel("to use this plugin");
        line3.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        line3.setFont(UIConstants.deriveFont(line3.getFont(), UIConstants.FONT_SIZE_NORMAL));
        line3.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(line3);

        panel.add(Box.createVerticalGlue());

        return panel;
    }

    /** Wraps a panel in a vertical-only scroll pane that fills available width. */
    private JScrollPane scrollWrap(JPanel panel) {
        // If the panel implements Scrollable with getScrollableTracksViewportWidth()=true,
        // put it directly as the viewport view — the Scrollable contract handles width.
        // Otherwise use the northWrapper pattern to pin content to top.
        final JComponent viewportView;
        if (panel instanceof Scrollable && ((Scrollable) panel).getScrollableTracksViewportWidth()) {
            viewportView = panel;
        } else {
            JPanel northWrapper = new JPanel(new BorderLayout());
            northWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
            northWrapper.add(panel, BorderLayout.NORTH);
            viewportView = northWrapper;
        }

        JScrollPane scroll = new JScrollPane(viewportView,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * Show a specific sub-panel by card name.
     * Updates nav button active states.
     * Triggers lazy initialization and refresh on the target panel.
     */
    public void showCard(String card) {
        this.activeCard = card;
        
        // Lazy initialize the panel before showing it
        initializePanel(card);
        
        cardLayout.show(contentPanel, card);

        // Update nav button active states
        String[] cards = {CARD_HOME, CARD_LEADERBOARD, CARD_COMPETITION, CARD_HUB};
        for (int i = 0; i < navButtons.size() && i < cards.length; i++) {
            navButtons.get(i).setActive(cards[i].equals(card));
        }

        // Admin button highlight
        if (adminButton != null) {
            adminButton.setOpaque(CARD_ADMIN.equals(card));
            adminButton.setBackground(CARD_ADMIN.equals(card)
                ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
        }

        // Refresh the target panel's data
        refreshPanel(card);
    }

    /**
     * Force-refresh the currently visible panel, bypassing all throttle cooldowns.
     * Called by the refresh button in the nav bar.
     */
    public void forceRefreshActivePanel() {
        switch (activeCard) {
            case CARD_HOME:
                homePanel.forceRefreshAll();
                break;
            case CARD_LEADERBOARD:
                leaderboardPanel.forceRefresh();
                break;
            case CARD_COMPETITION:
                competitionPanel.forceRefresh();
                break;
            case CARD_HUB:
                hubPanel.refresh();
                break;
        }
    }

    /**
     * Lazy initialize a panel if it extends BasePanel.
     * This ensures the panel's UI is built before it's shown.
     */
    private void initializePanel(String card) {
        // Future: migrate panels to BasePanel and add them here
    }

    /**
     * Refresh a specific panel's data.
     */
    private void refreshPanel(String card) {
        log.debug("[BoomerangPanel] refreshPanel() called for card: {}", card);
        switch (card) {
            case CARD_HOME:
                log.debug("[BoomerangPanel] Refreshing HOME panel data");
                homePanel.refresh();
                log.debug("[BoomerangPanel] HOME panel refresh complete");
                break;
            case CARD_LEADERBOARD:
                leaderboardPanel.refresh();
                break;
            case CARD_COMPETITION:
                competitionPanel.refresh();
                break;
            case CARD_HUB:
                hubPanel.refresh();
                break;
            case CARD_ADMIN:
                // Pre-populate announcement field with current value from config
                adminPanel.setCurrentAnnouncement(config.announcementMessage());
                break;
        }
    }

    // Legacy methods for Phase 1 compatibility
    public void onLogin(String rsn) {
        homePanel.updateGreeting(rsn);
        homePanel.updateStatus("Authenticating...", ColorScheme.LIGHT_GRAY_COLOR);
    }

    public void onAuthenticated() {
        showUnlocked();
        homePanel.updateStatus("Connected", new Color(0x4CAF50));
    }

    public void onDegraded() {
        homePanel.updateStatus("Degraded mode", new Color(0xFFC107));
    }

    public void onLogout() {
        showLocked();
        homePanel.updateGreeting("Adventurer");
        homePanel.updateStatus("Not connected", ColorScheme.LIGHT_GRAY_COLOR);
    }

    /** Show the locked screen — hides nav bar, footer, and all content panels. */
    public void showLocked() {
        cardLayout.show(contentPanel, CARD_LOCKED);
        activeCard = CARD_LOCKED;
        if (navBar != null) navBar.setVisible(false);
        if (stickyFooter != null) stickyFooter.setVisible(false);
    }

    /** Unlock the panel — show nav bar, footer, and navigate to home. */
    public void showUnlocked() {
        if (navBar != null) navBar.setVisible(true);
        if (stickyFooter != null) stickyFooter.setVisible(true);
        showCard(CARD_HOME);
    }

    public void refreshConfig() {
        log.debug("[BoomerangPanel] refreshConfig() called");
        homePanel.forceRefreshAll();
    }

    /**
     * Update admin button visibility based on player's clan rank.
     * Reads ClanSettings on the client thread, then updates UI on the EDT.
     */
    public void updateAdminVisibility() {
        log.debug("[BoomerangPanel] updateAdminVisibility() called");
        clientThread.invoke(() -> {
            boolean isAdmin = clanValidator.isAdmin();
            log.debug("[BoomerangPanel] isAdmin result: {}", isAdmin);
            SwingUtilities.invokeLater(() -> {
                adminButton.setVisible(isAdmin);
                log.info("[BoomerangPanel] Admin button visibility set to: {}", isAdmin);
            });
        });
    }

}
