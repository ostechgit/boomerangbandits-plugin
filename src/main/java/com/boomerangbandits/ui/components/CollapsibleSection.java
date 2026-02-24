package com.boomerangbandits.ui.components;

import com.boomerangbandits.ui.UIConstants;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * A collapsible panel section with a clickable header.
 * <p>
 * Uses BoxLayout Y_AXIS so width is always driven by the parent container,
 * never by the content's unconstrained preferred width. This prevents
 * horizontal overflow when placed inside a scrollWrap() panel.
 */
public class CollapsibleSection extends JPanel {
    private final JPanel contentPanel;
    private final JLabel arrowLabel;
    private final JPanel headerPanel;
    @Getter
    private boolean expanded;

    public CollapsibleSection(String title, JPanel content, boolean startExpanded) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(0, 0, UIConstants.SPACING_ITEM, 0));
        setAlignmentX(LEFT_ALIGNMENT);

        this.expanded = startExpanded;
        this.contentPanel = content;
        contentPanel.setVisible(expanded);
        // Content must also not push width — cap it
        contentPanel.setAlignmentX(LEFT_ALIGNMENT);
        contentPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Header row: arrow on left, title fills rest
        headerPanel = new JPanel(new BorderLayout(UIConstants.SPACING_SMALL, 0));
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(
                UIConstants.SPACING_SMALL,
                UIConstants.PADDING_STANDARD,
                UIConstants.SPACING_SMALL,
                UIConstants.PADDING_STANDARD
        ));
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setAlignmentX(LEFT_ALIGNMENT);
        headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        arrowLabel = new JLabel(expanded ? "v" : ">");
        arrowLabel.setFont(UIConstants.deriveFont(arrowLabel.getFont(), UIConstants.FONT_SIZE_SMALL));
        arrowLabel.setForeground(ColorScheme.BRAND_ORANGE);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UIConstants.deriveFont(titleLabel.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        titleLabel.setForeground(Color.WHITE);

        headerPanel.add(arrowLabel, BorderLayout.WEST);
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    setExpanded(!expanded);
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                headerPanel.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });

        add(headerPanel);
        add(contentPanel);
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        arrowLabel.setText(expanded ? "v" : ">");
        contentPanel.setVisible(expanded);
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        if (getParent() != null && getParent().getWidth() > 0) {
            return new Dimension(getParent().getWidth(), pref.height);
        }
        return pref;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
