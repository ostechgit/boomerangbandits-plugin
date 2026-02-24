package com.boomerangbandits.ui;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Centralized UI constants for consistent styling across all panels.
 * 
 * Modify these values to adjust the look and feel of the entire plugin.
 */
public class UIConstants {

    // ======================================================================
    // FONT SIZES
    // ======================================================================
    
    /** Large heading text (e.g., panel titles, main headers) */
    public static final float FONT_SIZE_LARGE = 14f;
    
    /** Medium heading text (e.g., section headers) */
    public static final float FONT_SIZE_MEDIUM = 12f;
    
    /** Normal body text (e.g., labels, descriptions) */
    public static final float FONT_SIZE_NORMAL = 11f;
    
    /** Small text (e.g., secondary info, timestamps) */
    public static final float FONT_SIZE_SMALL = 10f;
    
    /** Extra large display text (e.g., points totals, big numbers) */
    public static final float FONT_SIZE_DISPLAY = 24f;
    
    // ======================================================================
    // FONT STYLES
    // ======================================================================
    
    public static final int FONT_BOLD = Font.BOLD;
    public static final int FONT_PLAIN = Font.PLAIN;
    public static final int FONT_ITALIC = Font.ITALIC;
    
    // ======================================================================
    // SPACING
    // ======================================================================
    
    /** Standard padding inside components */
    public static final int PADDING_STANDARD = 8;
    
    /** Large padding for major sections */
    public static final int PADDING_LARGE = 12;
    
    /** Small padding for compact layouts */
    public static final int PADDING_SMALL = 4;
    
    /** Vertical spacing between sections */
    public static final int SPACING_SECTION = 16;
    
    /** Vertical spacing between items */
    public static final int SPACING_ITEM = 8;
    
    /** Small vertical spacing */
    public static final int SPACING_SMALL = 4;
    
    // ======================================================================
    // COMPONENT HEIGHTS
    // ======================================================================
    
    /** Standard row height for list items */
    public static final int ROW_HEIGHT_STANDARD = 24;
    
    /** Compact row height */
    public static final int ROW_HEIGHT_COMPACT = 20;
    
    /** Large row height for prominent items */
    public static final int ROW_HEIGHT_LARGE = 30;
    
    /** Points display box height */
    public static final int POINTS_DISPLAY_HEIGHT = 80;
    
    // ======================================================================
    // HELPER METHODS
    // ======================================================================
    
    /**
     * Create a font with the specified size and style.
     * 
     * @param baseFont the base font to derive from
     * @param size one of the FONT_SIZE_* constants
     * @param style one of the FONT_* style constants
     * @return derived font
     */
    public static Font deriveFont(Font baseFont, float size, int style) {
        return baseFont.deriveFont(style, size);
    }
    
    /**
     * Create a font with the specified size (plain style).
     * 
     * @param baseFont the base font to derive from
     * @param size one of the FONT_SIZE_* constants
     * @return derived font
     */
    public static Font deriveFont(Font baseFont, float size) {
        return baseFont.deriveFont(size);
    }
    
    /**
     * Apply high-quality text rendering hints to a Graphics2D context.
     * This improves text clarity on lower resolution displays (1440p and below).
     * 
     * @param g2d the Graphics2D context to configure
     */
    public static void applyTextRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
    
    /**
     * Capitalize the first letter of a string, preserving the rest.
     *
     * @param str the string to capitalize
     * @return the capitalized string, or the original if null/empty
     */
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Capitalize the first letter and lowercase the rest.
     *
     * @param str the string to capitalize
     * @return the capitalized string, or the original if null/empty
     */
    public static String capitalizeLower(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private UIConstants() {
        // Utility class, no instantiation
    }
}
