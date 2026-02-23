package com.boomerangbandits.ui.components;

import com.boomerangbandits.ui.UIConstants;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Sticky footer bar pinned to the bottom of BoomerangPanel (outside the scroll area).
 * Contains a single Refresh button that calls the supplied action.
 * <p>
 * Loading state is reset automatically after a fixed timeout so it never gets stuck.
 */
public class PanelFooter extends JPanel {

    private static final int LOADING_TIMEOUT_MS = 5_000;

    private final JButton refreshBtn;
    private boolean loading = false;

    public PanelFooter(Runnable onRefresh) {
        setLayout(new GridBagLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new MatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR));
        setPreferredSize(new Dimension(0, 28));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        refreshBtn = new JButton("Refresh");
        refreshBtn.setFont(UIConstants.deriveFont(refreshBtn.getFont(), UIConstants.FONT_SIZE_SMALL));
        refreshBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        refreshBtn.setBorder(null);
        refreshBtn.setContentAreaFilled(false);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.addActionListener(e -> {
            if (!loading) {
                setLoading(true);
                onRefresh.run();
                // Safety timeout — re-enable after 5s in case the panel never calls back
                Timer timer = new Timer(LOADING_TIMEOUT_MS, ev -> resetLoading());
                timer.setRepeats(false);
                timer.start();
            }
        });

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(0, UIConstants.PADDING_SMALL, 0, UIConstants.PADDING_SMALL);
        add(refreshBtn, c);
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
        SwingUtilities.invokeLater(() -> {
            refreshBtn.setText(loading ? "Loading..." : "Refresh");
            refreshBtn.setEnabled(!loading);
        });
    }

    public void resetLoading() {
        setLoading(false);
    }
}
