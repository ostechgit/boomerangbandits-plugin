package com.boomerangbandits.ui.panels;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.api.models.RankSummaryResponse;
import com.boomerangbandits.ui.UIConstants;
import com.boomerangbandits.ui.components.AntialiasedLabel;
import com.boomerangbandits.ui.components.AntialiasedTextArea;
import com.boomerangbandits.ui.components.CollapsibleSection;
import com.boomerangbandits.ui.components.WidthConstrainedPanel;
import com.boomerangbandits.util.RefreshThrottler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

@Slf4j
public class ClanHubPanel extends JPanel implements Scrollable {

    private final BoomerangBanditsConfig config;
    private final ClanApiService clanApi;
    private final RefreshThrottler rosterThrottler = new RefreshThrottler(60 * 60 * 1_000L);
    private JPanel linksPanel;
    private JPanel rosterContent;
    private AntialiasedTextArea dinkUrlLabel;

    @Inject
    public ClanHubPanel(BoomerangBanditsConfig config, ClanApiService clanApi) {
        this.config = config;
        this.clanApi = clanApi;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        buildUI();
    }

    private void buildUI() {
        JLabel titleLabel = new AntialiasedLabel("Clan Hub");
        titleLabel.setFont(FontManager.getRunescapeBoldFont());
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, titleLabel.getPreferredSize().height));
        add(titleLabel);

        add(Box.createVerticalStrut(10));

        JLabel welcomeLabel = new AntialiasedLabel("Welcome to Boomerang Bandits!");
        welcomeLabel.setForeground(Color.WHITE);
        welcomeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        welcomeLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, welcomeLabel.getPreferredSize().height));
        add(welcomeLabel);

        add(Box.createVerticalStrut(15));

        // Quick Links
        JLabel linksTitle = new AntialiasedLabel("Quick Links");
        linksTitle.setFont(FontManager.getRunescapeBoldFont());
        linksTitle.setForeground(ColorScheme.BRAND_ORANGE);
        linksTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        linksTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, linksTitle.getPreferredSize().height));
        add(linksTitle);

        add(Box.createVerticalStrut(5));

        linksPanel = new JPanel();
        linksPanel.setLayout(new BoxLayout(linksPanel, BoxLayout.Y_AXIS));
        linksPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        linksPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        linksPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        linksPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        add(linksPanel);

        add(Box.createVerticalStrut(15));

        // Dink Setup
        CollapsibleSection dinkSection = new CollapsibleSection("Dink Webhook Setup", buildDinkContent(), false);
        add(dinkSection);

        add(Box.createVerticalStrut(15));

        // Member Roster — collapsed by default
        rosterContent = buildRosterContent();
        CollapsibleSection rosterSection = new CollapsibleSection("Member Roster", rosterContent, false);
        add(rosterSection);

        add(Box.createVerticalStrut(5));

        // Info & Ranking
        CollapsibleSection infoSection = new CollapsibleSection("Info & Ranking", createInfoContent(), false);
        add(infoSection);

        // Populate static/config-driven content only — no API calls here.
        // refreshRoster() is called lazily when the panel is shown post-auth.
        refreshLinks();
        refreshDinkUrl();
    }

    public void refresh() {
        refreshLinks();
        refreshDinkUrl();
        refreshRoster();
    }

    private void refreshLinks() {
        linksPanel.removeAll();

        String website = config.websiteUrl();
        if (website != null && !website.isEmpty()) {
            addLinkButton("Website", website);
        }

        String discord = config.discordUrl();
        if (discord != null && !discord.isEmpty()) {
            addLinkButton("Discord", discord);
        }

        addLinkButton("Wise Old Man", "https://wiseoldman.net/groups/11575");

        if (linksPanel.getComponentCount() == 0) {
            JLabel noLinks = new AntialiasedLabel("No links configured");
            noLinks.setForeground(Color.GRAY);
            linksPanel.add(noLinks);
        }

        linksPanel.revalidate();
        linksPanel.repaint();
    }

    private void refreshDinkUrl() {
        if (dinkUrlLabel != null) {
            String dinkUrl = config.dinkConfigUrl();
            if (dinkUrl != null && !dinkUrl.isEmpty()) {
                dinkUrlLabel.setText(dinkUrl);
                dinkUrlLabel.setForeground(Color.WHITE);
            } else {
                dinkUrlLabel.setText("Waiting for config sync...");
                dinkUrlLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            }
        }
    }

    private void refreshRoster() {
        if (!rosterThrottler.shouldRefresh()) {
            return;
        }
        rosterThrottler.recordRefresh();
        clanApi.fetchRankSummary(
                false,
                summary -> SwingUtilities.invokeLater(() -> updateRosterContent(summary)),
                error -> log.debug("[ClanHubPanel] Could not fetch rank summary: {}", error)
        );
    }

    private JPanel buildRosterContent() {
        WidthConstrainedPanel content = new WidthConstrainedPanel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(
                UIConstants.PADDING_SMALL, UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD
        ));

        JLabel loading = new AntialiasedLabel("Loading...");
        loading.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loading.setFont(FontManager.getRunescapeSmallFont());
        loading.setAlignmentX(LEFT_ALIGNMENT);
        loading.setMaximumSize(new Dimension(Integer.MAX_VALUE, loading.getPreferredSize().height));
        content.add(loading);

        return content;
    }

    private void updateRosterContent(RankSummaryResponse summary) {
        rosterContent.removeAll();

        JLabel totalLabel = new AntialiasedLabel("Total Members: " + summary.getTotalMembers());
        totalLabel.setForeground(Color.WHITE);
        totalLabel.setFont(FontManager.getRunescapeBoldFont());
        totalLabel.setAlignmentX(LEFT_ALIGNMENT);
        totalLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, totalLabel.getPreferredSize().height));
        rosterContent.add(totalLabel);
        rosterContent.add(Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        List<RankSummaryResponse.RankCount> ranks = summary.getRanks();
        if (ranks != null) {
            for (RankSummaryResponse.RankCount rc : ranks) {
                if (rc.getCount() > 0) {
                    rosterContent.add(buildRankRow(UIConstants.capitalize(rc.getRank()), rc.getCount()));
                }
            }
        }

        rosterContent.revalidate();
        rosterContent.repaint();
    }

    private JPanel buildRankRow(String rank, int count) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setBorder(new EmptyBorder(2, 0, 2, 0));

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 4);
        JLabel rankLabel = new AntialiasedLabel(rank);
        rankLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rankLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(rankLabel, c);

        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(0, 0, 0, 0);
        JLabel countLabel = new AntialiasedLabel(count + " members");
        countLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        countLabel.setFont(FontManager.getRunescapeSmallFont());
        row.add(countLabel, c);

        return row;
    }

    private JPanel buildDinkContent() {
        WidthConstrainedPanel content = new WidthConstrainedPanel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(
                UIConstants.PADDING_SMALL, UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD
        ));

        // HTML label wraps correctly because scrollWrap gives it a bounded width
        JLabel desc = new AntialiasedLabel("<html>To enable automatic webhook tracking, paste the URL below into "
                + "Dink's settings: <b>Advanced &gt; Dynamic Config URL</b></html>");
        desc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        desc.setFont(FontManager.getRunescapeSmallFont());
        desc.setAlignmentX(LEFT_ALIGNMENT);
        desc.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.add(desc);

        content.add(Box.createVerticalStrut(8));

        // Read-only JTextArea — wraps long URLs, no horizontal scroll
        dinkUrlLabel = new AntialiasedTextArea("Waiting for config sync...");
        dinkUrlLabel.setEditable(false);
        dinkUrlLabel.setFocusable(false);
        dinkUrlLabel.setLineWrap(true);
        dinkUrlLabel.setWrapStyleWord(false); // URLs should break mid-word
        dinkUrlLabel.setColumns(0);           // don't use column count for preferred width
        dinkUrlLabel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        dinkUrlLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        dinkUrlLabel.setFont(FontManager.getRunescapeSmallFont());
        dinkUrlLabel.setBorder(new EmptyBorder(4, 4, 4, 4));
        dinkUrlLabel.setAlignmentX(LEFT_ALIGNMENT);
        dinkUrlLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        content.add(dinkUrlLabel);

        content.add(Box.createVerticalStrut(6));

        JButton copyBtn = new JButton("Copy URL");
        copyBtn.setAlignmentX(LEFT_ALIGNMENT);
        copyBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, copyBtn.getPreferredSize().height));
        copyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyBtn.addActionListener(e -> {
            String url = config.dinkConfigUrl();
            if (url != null && !url.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(url), null);
                copyBtn.setText("Copied!");
                Timer reset = new Timer(2000, ev -> copyBtn.setText("Copy URL"));
                reset.setRepeats(false);
                reset.start();
            }
        });
        content.add(copyBtn);

        return content;
    }

    private void addLinkButton(String title, String url) {
        JButton button = new JButton(title);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> LinkBrowser.browse(url));
        linksPanel.add(button);
        linksPanel.add(Box.createVerticalStrut(3));
    }


    private JPanel createInfoContent() {
        WidthConstrainedPanel content = new WidthConstrainedPanel(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(
                UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD
        ));

        content.add(createInfoSectionHeader("General"));
        content.add(createPointsRow("Recruit a clan member", "5 pts"));
        content.add(createInfoText("Post in #recruitment channel."));
        content.add(createPointsRow("Helpful clanmate", "2 pts"));
        content.add(createInfoText("Teaching, quest help, 99 attendance, etc. Proof required in #appreciation-for-clannies."));

        content.add(createInfoSectionHeader("Competitions & Events"));
        content.add(createPointsRow("Participate in an event", "4 pts"));
        content.add(createPointsRow("1st place", "10 pts"));
        content.add(createPointsRow("2nd place", "8 pts"));
        content.add(createPointsRow("3rd place", "5 pts"));
        content.add(createPointsRow("Win a KOTC", "4 pts"));

        content.add(createInfoSectionHeader("Earning Capes"));
        content.add(createInfoText("Only capes earned while in the clan count. Post proof in #loot-and-achievements."));
        content.add(createPointsRow("99 in any skill", "10 pts"));
        content.add(createPointsRow("Quest Cape", "10 pts"));
        content.add(createPointsRow("Infernal Cape", "10 pts"));
        content.add(createPointsRow("Music Cape", "10 pts"));
        content.add(createPointsRow("Max Cape", "20 pts"));

        content.add(createInfoSectionHeader("Loyalty Bonus"));
        content.add(createPointsRow("Every month in the clan", "10 pts"));

        content.add(createInfoSectionHeader("Donations"));
        content.add(createPointsRow("1M donated", "1 pt"));
        content.add(createInfoText("Rounded up — e.g. 2.5M = 3 pts. Capped at 10 pts per month."));

        return content;
    }

    private JLabel createInfoSectionHeader(String text) {
        JLabel label = new AntialiasedLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setBorder(new EmptyBorder(UIConstants.PADDING_LARGE, 0, UIConstants.SPACING_SMALL, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return label;
    }

    private JLabel createInfoText(String text) {
        JLabel label = new AntialiasedLabel("<html>" + text + "</html>");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setBorder(new EmptyBorder(2, 0, UIConstants.SPACING_ITEM, 0));
        label.setAlignmentX(LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return label;
    }

    private JPanel createPointsRow(String activity, String points) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(UIConstants.SPACING_SMALL, UIConstants.PADDING_STANDARD,
                        UIConstants.SPACING_SMALL, UIConstants.PADDING_STANDARD)
        ));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        GridBagConstraints c = new GridBagConstraints();

        JLabel actLabel = new AntialiasedLabel(activity);
        actLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        actLabel.setFont(FontManager.getRunescapeSmallFont());
        c.gridx = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        row.add(actLabel, c);

        JLabel ptsLabel = new AntialiasedLabel(points);
        ptsLabel.setForeground(new Color(0x4CAF50));
        ptsLabel.setFont(FontManager.getRunescapeBoldFont());
        c.gridx = 1;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        row.add(ptsLabel, c);

        return row;
    }

    // =========================================================================
    // Scrollable — tells JScrollPane to always size us to viewport width,
    // never to our preferred width. This is the definitive fix for horizontal
    // overflow: no matter what preferred widths our children report, the scroll
    // container will never grow wider than its viewport.
    // =========================================================================

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
        return 16;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
