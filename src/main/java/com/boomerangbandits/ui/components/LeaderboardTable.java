package com.boomerangbandits.ui.components;

import com.boomerangbandits.ui.UIConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import net.runelite.client.ui.ColorScheme;

/**
 * Reusable leaderboard table with rank, name, and value columns.
 * Styled to match RuneLite's dark theme.
 * <p>
 * Usage:
 *   LeaderboardTable table = new LeaderboardTable("RSN", "Points");
 *   table.setData(entries); // List<String[]> where each is {rank, name, value}
 */
public class LeaderboardTable extends JPanel {

    private final LeaderboardTableModel model;
    private final JTable table;
    private final JLabel emptyLabel;
    private JScrollPane scrollPane;

    public LeaderboardTable(String nameHeader, String valueHeader) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setAlignmentX(LEFT_ALIGNMENT);

        model = new LeaderboardTableModel(nameHeader, valueHeader);
        table = new JTable(model);
        table.setBackground(ColorScheme.DARK_GRAY_COLOR);
        table.setForeground(Color.WHITE);
        table.setGridColor(ColorScheme.MEDIUM_GRAY_COLOR);
        table.setRowHeight(UIConstants.ROW_HEIGHT_STANDARD);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        table.getTableHeader().setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        table.getTableHeader().setFont(UIConstants.deriveFont(
            table.getFont(),
            UIConstants.FONT_SIZE_NORMAL,
            UIConstants.FONT_BOLD
        ));

        // Column widths: rank=40, name=flex, value=60
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(0).setMinWidth(30);
        table.getColumnModel().getColumn(2).setMaxWidth(80);
        table.getColumnModel().getColumn(2).setMinWidth(60);

        // Custom cell renderer for rank highlighting (top 3)
        table.getColumnModel().getColumn(0).setCellRenderer(new RankRenderer());

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        scrollPane.setAlignmentX(LEFT_ALIGNMENT);
        // Width=0 means "take all available width" from the BoxLayout parent.
        // Height is set dynamically in setData() based on row count.
        scrollPane.setPreferredSize(new Dimension(0, 60));
        add(scrollPane);

        emptyLabel = new JLabel("No data available");
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyLabel.setHorizontalAlignment(JLabel.CENTER);
        emptyLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
    }

    /**
     * Update the table data.
     * @param rows list of String[3] arrays: {rank, name, value}
     */
    public void setData(List<String[]> rows) {
        model.setData(rows);
        removeAll();
        if (rows == null || rows.isEmpty()) {
            emptyLabel.setAlignmentX(LEFT_ALIGNMENT);
            add(emptyLabel);
        } else {
            // Resize scroll pane height to fit all rows — outer scrollWrap handles page scroll
            int headerHeight = table.getTableHeader().getPreferredSize().height;
            int height = headerHeight + (model.getRowCount() * table.getRowHeight()) + 4;
            scrollPane.setPreferredSize(new Dimension(0, Math.max(height, 60)));
            add(scrollPane);
        }
        revalidate();
        repaint();
    }

    // Table model
    private static class LeaderboardTableModel extends AbstractTableModel {
        private final String[] headers;
        private List<String[]> data = List.of();

        LeaderboardTableModel(String nameHeader, String valueHeader) {
            this.headers = new String[]{"#", nameHeader, valueHeader};
        }

        void setData(List<String[]> data) {
            this.data = data != null ? data : List.of();
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return data.size(); }
        @Override public int getColumnCount() { return 3; }
        @Override public String getColumnName(int col) { return headers[col]; }
        @Override public Object getValueAt(int row, int col) {
            return row < data.size() && col < data.get(row).length ? data.get(row)[col] : "";
        }
    }

    // Rank cell renderer — highlights top 3
    private static class RankRenderer extends DefaultTableCellRenderer {
        private static final Color GOLD = new Color(0xFFD700);
        private static final Color SILVER = new Color(0xC0C0C0);
        private static final Color BRONZE = new Color(0xCD7F32);

        @Override
        public Component getTableCellRendererComponent(JTable t, Object val,
                boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, val, sel, focus, row, col);
            setHorizontalAlignment(CENTER);
            setBackground(ColorScheme.DARK_GRAY_COLOR);
            switch (row) {
                case 0: setForeground(GOLD); break;
                case 1: setForeground(SILVER); break;
                case 2: setForeground(BRONZE); break;
                default: setForeground(Color.WHITE);
            }
            return this;
        }
    }
}
