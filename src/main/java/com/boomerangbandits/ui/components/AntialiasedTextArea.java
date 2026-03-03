package com.boomerangbandits.ui.components;

import javax.swing.*;

/**
 * A JTextArea subclass used throughout the plugin for semantic consistency.
 * <p>
 * Text clarity is achieved by using RuneLite's built-in FontManager fonts
 * (getRunescapeSmallFont, getRunescapeBoldFont) rather than rendering hints.
 * This matches the pattern used by all production RuneLite plugins.
 */
public class AntialiasedTextArea extends JTextArea {

    public AntialiasedTextArea() {
        super();
    }

    public AntialiasedTextArea(String text) {
        super(text);
    }

    public AntialiasedTextArea(int rows, int columns) {
        super(rows, columns);
    }
}
