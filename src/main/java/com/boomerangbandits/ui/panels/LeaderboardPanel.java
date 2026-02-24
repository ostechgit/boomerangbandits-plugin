package com.boomerangbandits.ui.panels;

import com.boomerangbandits.api.ClanApiService;
import com.boomerangbandits.api.models.LeaderboardEntry;
import com.boomerangbandits.ui.UIConstants;
import com.boomerangbandits.ui.components.LeaderboardTable;
import com.boomerangbandits.util.RefreshThrottler;
import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Clan leaderboard panel showing points-based rankings.
 * <p>
 * Data source: Backend GET /api/leaderboard (paginated)
 * Uses LeaderboardTable reusable component.
 */
public class LeaderboardPanel extends JPanel {

    private static final int PER_PAGE = 50;

    private final ClanApiService clanApi;
    private final LeaderboardTable table;
    private final RefreshThrottler refreshThrottler;

    private final JLabel pageLabel;
    private final JButton prevButton;
    private final JButton nextButton;
    private int currentPage = 1;
    private int totalPages = 1;

    @Inject
    public LeaderboardPanel(ClanApiService clanApi) {
        this.clanApi = clanApi;
        this.refreshThrottler = new RefreshThrottler(60 * 60 * 1_000); // 1 hour

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header
        JLabel header = new JLabel("Clan Leaderboard");
        header.setForeground(java.awt.Color.WHITE);
        header.setFont(UIConstants.deriveFont(header.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        header.setBorder(new EmptyBorder(
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD
        ));
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);

        // Table
        table = new LeaderboardTable("Player", "Points");
        table.setAlignmentX(LEFT_ALIGNMENT);
        add(table);

        // Pagination
        JPanel pagination = new JPanel(new FlowLayout(FlowLayout.CENTER, UIConstants.SPACING_ITEM, UIConstants.SPACING_SMALL));
        pagination.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        pagination.setAlignmentX(LEFT_ALIGNMENT);

        prevButton = new JButton("<");
        prevButton.setEnabled(false);
        prevButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        prevButton.addActionListener(e -> loadPage(currentPage - 1));
        pagination.add(prevButton);

        pageLabel = new JLabel("1 / 1");
        pageLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        pagination.add(pageLabel);

        nextButton = new JButton(">");
        nextButton.setEnabled(false);
        nextButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        nextButton.addActionListener(e -> loadPage(currentPage + 1));
        pagination.add(nextButton);

        add(pagination);
    }

    /**
     * Load and display a specific page.
     * Safe to call from any thread.
     */
    public void loadPage(int page) {
        clanApi.fetchLeaderboard(page, PER_PAGE,
                response -> SwingUtilities.invokeLater(() -> {
                    currentPage = response.getPage();
                    totalPages = response.getPages();

                    List<String[]> rows = new ArrayList<>();
                    for (LeaderboardEntry entry : response.getLeaderboard()) {
                        rows.add(new String[]{
                                String.valueOf(entry.getRank()),
                                entry.getRsn(),
                                String.format("%,d", entry.getTotalPoints())
                        });
                    }
                    table.setData(rows);

                    pageLabel.setText(currentPage + " / " + totalPages);
                    prevButton.setEnabled(currentPage > 1);
                    nextButton.setEnabled(currentPage < totalPages);
                }),
                error -> SwingUtilities.invokeLater(() ->
                        table.setData(null)
                )
        );
    }

    /**
     * Refresh the current page.
     * Throttled to prevent excessive API calls (30s cooldown).
     */
    public void refresh() {
        if (!refreshThrottler.shouldRefresh()) {
            return; // Data is fresh enough
        }
        refreshThrottler.recordRefresh();
        loadPage(currentPage);
    }

    /**
     * Force refresh (bypasses cooldown).
     */
    public void forceRefresh() {
        refreshThrottler.reset();
        refresh();
    }
}
