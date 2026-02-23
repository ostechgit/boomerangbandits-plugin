package com.boomerangbandits.ui.panels;

import javax.annotation.Nonnull;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import net.runelite.client.ui.ColorScheme;

/**
 * Abstract base class for all plugin panels.
 * 
 * <p>Provides common functionality:</p>
 * <ul>
 *   <li>Lazy initialization - UI is built only when first shown</li>
 *   <li>Standard background color</li>
 *   <li>Helper methods for creating scroll panes</li>
 *   <li>Optional refresh capability</li>
 * </ul>
 * 
 * <p>Subclasses should:</p>
 * <ul>
 *   <li>Override {@link #buildUI()} to construct the panel's UI</li>
 *   <li>Optionally override {@link #refresh()} if the panel needs refresh capability</li>
 *   <li>Call {@link #ensureInitialized()} before accessing UI components</li>
 * </ul>
 * 
 * <h3>Example Usage:</h3>
 * <pre>
 * public class MyPanel extends BasePanel {
 *     private JLabel myLabel;
 *     
 *     {@literal @}Override
 *     protected void buildUI() {
 *         setLayout(new BorderLayout());
 *         myLabel = new JLabel("Hello");
 *         add(myLabel, BorderLayout.CENTER);
 *     }
 *     
 *     {@literal @}Override
 *     public void refresh() {
 *         ensureInitialized();
 *         // Update data
 *     }
 * }
 * </pre>
 */
public abstract class BasePanel extends JPanel {
    
    private volatile boolean initialized = false;
    
    /**
     * Create a new base panel with standard background color.
     * UI is NOT built until {@link #ensureInitialized()} is called.
     */
    protected BasePanel() {
        setBackground(ColorScheme.DARK_GRAY_COLOR);
    }
    
    /**
     * Ensure the panel's UI has been initialized.
     * This method is idempotent - safe to call multiple times.
     * 
     * <p>The first call will trigger {@link #buildUI()}, subsequent calls do nothing.</p>
     * 
     * <p>This should be called:</p>
     * <ul>
     *   <li>Before the panel is first shown</li>
     *   <li>At the start of {@link #refresh()} if overridden</li>
     *   <li>Before accessing any UI components</li>
     * </ul>
     */
    public final void ensureInitialized() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    buildUI();
                    initialized = true;
                }
            }
        }
    }
    
    /**
     * Build the panel's UI components.
     * 
     * <p>This method is called exactly once, the first time {@link #ensureInitialized()}
     * is invoked. Subclasses should construct all UI components here.</p>
     * 
     * <p>Do NOT call this method directly - use {@link #ensureInitialized()} instead.</p>
     */
    protected abstract void buildUI();
    
    /**
     * Refresh the panel's data.
     * 
     * <p>Default implementation does nothing. Override if the panel needs refresh capability.</p>
     * 
     * <p>Implementations should call {@link #ensureInitialized()} first to ensure
     * UI components exist before updating them.</p>
     */
    public void refresh() {
        // Default: no-op
        // Subclasses can override if they need refresh capability
    }
    
    /**
     * Check if the panel has been initialized.
     * 
     * @return true if {@link #buildUI()} has been called, false otherwise
     */
    public final boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Create a standard scroll pane for panel content.
     * 
     * <p>Configured with:</p>
     * <ul>
     *   <li>No border</li>
     *   <li>Vertical scrollbar as needed</li>
     *   <li>No horizontal scrollbar</li>
     *   <li>Smooth scrolling (16px per unit)</li>
     *   <li>Standard background color</li>
     * </ul>
     * 
     * @param content the panel to wrap in a scroll pane (must not be null)
     * @return configured scroll pane (never null)
     */
    @Nonnull
    protected JScrollPane createScrollPane(@Nonnull JPanel content) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        return scrollPane;
    }
    
    /**
     * Create a standard scroll pane with custom scroll bar policies.
     * 
     * @param content the panel to wrap in a scroll pane (must not be null)
     * @param horizontalPolicy horizontal scroll bar policy (e.g., HORIZONTAL_SCROLLBAR_NEVER)
     * @param verticalPolicy vertical scroll bar policy (e.g., VERTICAL_SCROLLBAR_AS_NEEDED)
     * @return configured scroll pane (never null)
     */
    @Nonnull
    protected JScrollPane createScrollPane(@Nonnull JPanel content, int horizontalPolicy, int verticalPolicy) {
        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(horizontalPolicy);
        scrollPane.setVerticalScrollBarPolicy(verticalPolicy);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        return scrollPane;
    }
}
