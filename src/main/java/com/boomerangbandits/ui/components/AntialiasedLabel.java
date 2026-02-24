package com.boomerangbandits.ui.components;

import com.boomerangbandits.ui.UIConstants;

import javax.swing.*;
import java.awt.*;

/**
 * A JLabel that applies high-quality text rendering hints for improved clarity
 * on lower resolution displays (1440p and below).
 * <p>
 * This component ensures text is rendered with proper anti-aliasing and fractional
 * metrics, preventing blurry or pixelated text.
 */
public class AntialiasedLabel extends JLabel {

    public AntialiasedLabel() {
        super();
    }

    public AntialiasedLabel(String text) {
        super(text);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
            UIConstants.applyTextRenderingHints(g2d);
            super.paintComponent(g2d);
        } finally {
            g2d.dispose();
        }
    }
}
