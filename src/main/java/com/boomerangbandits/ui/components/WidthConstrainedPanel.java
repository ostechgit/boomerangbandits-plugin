package com.boomerangbandits.ui.components;

import javax.swing.*;
import java.awt.*;

/**
 * A JPanel that overrides getPreferredSize() to never report a width wider
 * than its parent's current width.
 *
 * <p>This breaks the bottom-up preferred-size propagation that causes horizontal
 * overflow in BoxLayout panels. Normally, BoxLayout asks each child for its
 * preferred width and uses the maximum as the panel's own preferred width —
 * which then propagates up to the scroll container and triggers a horizontal
 * scrollbar. By capping preferred width to the parent's actual width, we
 * ensure the scroll container never sees a width wider than the viewport.</p>
 *
 * <p>Use this as the content panel for CollapsibleSection and any other
 * BoxLayout container whose children might have wide preferred sizes.</p>
 */
public class WidthConstrainedPanel extends JPanel {

    public WidthConstrainedPanel(LayoutManager layout) {
        super(layout);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension pref = super.getPreferredSize();
        // If we have a parent, cap our preferred width to the parent's current width.
        // This prevents our children's unconstrained preferred widths from bubbling up.
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
