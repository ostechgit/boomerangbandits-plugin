package com.boomerangbandits.ui.components;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

/**
 * Small icon button for the panel navigation bar.
 * Supports active/inactive states with visual feedback.
 *
 * Usage:
 *   NavButton btn = new NavButton(icon, "Home", () -> showPanel("HOME"));
 *   navBar.add(btn);
 */
public class NavButton extends JButton {

    private static final int SIZE = 32;
    private static final Color ACTIVE_BG = ColorScheme.DARK_GRAY_COLOR;
    private static final Color INACTIVE_BG = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color HOVER_BG = ColorScheme.DARK_GRAY_HOVER_COLOR;

    @Getter
    private boolean active = false;
    private final Runnable onClick;

    public NavButton(ImageIcon icon, String tooltip, Runnable onClick) {
        this.onClick = onClick;

        setIcon(icon);
        setToolTipText(tooltip);
        setPreferredSize(new Dimension(SIZE, SIZE));
        setMinimumSize(new Dimension(SIZE, SIZE));
        setMaximumSize(new Dimension(SIZE, SIZE));
        setBorder(new EmptyBorder(4, 4, 4, 4));
        setContentAreaFilled(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBackground(INACTIVE_BG);
        setOpaque(true);

        addActionListener(e -> onClick.run());
        addMouseAdapter();
    }

    public void setActive(boolean active) {
        this.active = active;
        setBackground(active ? ACTIVE_BG : INACTIVE_BG);
        repaint();
    }

    private void addMouseAdapter() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!active) setBackground(HOVER_BG);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(active ? ACTIVE_BG : INACTIVE_BG);
            }
        });
    }
}
