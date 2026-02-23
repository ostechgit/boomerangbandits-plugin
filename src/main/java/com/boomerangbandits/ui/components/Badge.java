package com.boomerangbandits.ui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * Small coloured badge for displaying rank, status, or category labels.
 * <p>
 * Usage:
 *   Badge = new Badge("Admin", new Color(0x4CAF50));
 *   panel.add(badge);
 */
public class Badge extends JComponent {

    private String text;
    private Color bgColor;
    private final Color textColor;

    private static final int PAD_X = 4;
    private static final int PAD_Y = 2;
    private static final int ARC = 10;

    public Badge(String text, Color bgColor) {
        this(text, bgColor, Color.WHITE);
    }

    public Badge(String text, Color bgColor, Color textColor) {
        this.text = text;
        this.bgColor = bgColor;
        this.textColor = textColor;
        setOpaque(false);
    }

    public void setText(String text) {
        this.text = text;
        revalidate();
        repaint();
    }

    public void setBgColor(Color bgColor) {
        this.bgColor = bgColor;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int width = fm.stringWidth(text) + PAD_X * 2;
        int height = fm.getHeight() + PAD_Y * 2;
        return new Dimension(width, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(bgColor);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);

        // Text
        g2.setFont(getFont());
        g2.setColor(textColor);
        FontMetrics fm = g2.getFontMetrics();
        int x = (getWidth() - fm.stringWidth(text)) / 2;
        int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(text, x, y);

        g2.dispose();
    }
}
