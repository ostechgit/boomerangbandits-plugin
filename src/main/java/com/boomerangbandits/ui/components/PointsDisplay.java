package com.boomerangbandits.ui.components;

import com.boomerangbandits.ui.UIConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * Displays a points total with optional label and breakdown.
 * <p>
 * Usage:
 *   PointsDisplay display = new PointsDisplay("Total Points");
 *   display.setPoints(1234);
 */
public class PointsDisplay extends JPanel {

    private final JLabel titleLabel;
    private final JLabel valueLabel;

    public PointsDisplay(String title) {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(new EmptyBorder(
            UIConstants.PADDING_LARGE,
            UIConstants.PADDING_LARGE,
            UIConstants.PADDING_LARGE,
            UIConstants.PADDING_LARGE
        ));
        setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, UIConstants.POINTS_DISPLAY_HEIGHT));

        titleLabel = new JLabel(title);
        titleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        titleLabel.setFont(UIConstants.deriveFont(titleLabel.getFont(), UIConstants.FONT_SIZE_NORMAL));
        add(titleLabel, BorderLayout.NORTH);

        valueLabel = new JLabel("0");
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(UIConstants.deriveFont(
            valueLabel.getFont(), 
            UIConstants.FONT_SIZE_DISPLAY, 
            UIConstants.FONT_BOLD
        ));
        valueLabel.setHorizontalAlignment(SwingConstants.LEFT);
        valueLabel.setBorder(new EmptyBorder(UIConstants.SPACING_SMALL, 0, 0, 0));
        add(valueLabel, BorderLayout.CENTER);
    }

    public void setPoints(int points) {
        valueLabel.setText(String.format("%,d", points));
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }
}
