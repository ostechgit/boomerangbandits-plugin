package com.boomerangbandits.ui.panels;

import com.boomerangbandits.api.AdminApiService;
import com.boomerangbandits.api.models.AttendanceEntry;
import com.boomerangbandits.api.models.AttendanceResult;
import com.boomerangbandits.api.models.RankChange;
import com.boomerangbandits.services.EventAttendanceTracker;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

/**
 * Admin panel — attendance ingestion, rank changes, announcements, roster sync.
 *
 * Layout rules:
 * - Top-level panel uses BoxLayout (Rule 2) — no internal JScrollPane (Rule 1)
 * - Every direct BoxLayout child has setMaximumSize + setAlignmentX(LEFT_ALIGNMENT) (Rule 4)
 * - Labels inside BoxLayout are wrapped in a GridBagLayout row panel (Rule 10)
 * - No FlowLayout for rows — use a fixed-height JPanel with GridBagLayout (Rule 5)
 */
@Slf4j
public class AdminPanel extends JPanel implements Scrollable {

    private final AdminApiService adminApi;
    @Setter
    private Runnable onGroupSync;
    private EventAttendanceTracker attendanceTracker;

    // Attendance section
    private JTextField eventNameField;
    private JSpinner thresholdSpinner;
    private JButton startEventButton;
    private JButton stopSubmitButton;
    private JLabel attendanceStatusLabel;
    private JLabel attendanceResultLabel;

    // Rank changes section
    private JPanel rankChangesContainer;
    private JButton refreshRankChangesButton;

    // Announcement section
    private JTextArea announcementTextArea;
    private JButton updateAnnouncementButton;
    private JLabel announcementResultLabel;

    // Roster sync section
    private JButton syncRosterButton;
    private JLabel syncRosterResultLabel;

    @Inject
    public AdminPanel(AdminApiService adminApi) {
        this(adminApi, null);
    }

    public AdminPanel(AdminApiService adminApi, Runnable onGroupSync) {
        this.adminApi = adminApi;
        this.onGroupSync = onGroupSync;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Admin Panel");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 0, 8, 0));
        add(title);

        add(buildAttendanceSection());
        add(javax.swing.Box.createVerticalStrut(8));
        add(buildRankChangesSection());
        add(javax.swing.Box.createVerticalStrut(8));
        add(buildAnnouncementSection());
        add(javax.swing.Box.createVerticalStrut(8));
        add(buildRosterSyncSection());
    }

    // =========================================================================
    // Section 1: Attendance Tracking
    // =========================================================================

    private JPanel buildAttendanceSection() {
        JPanel section = createSection("Event Attendance");

        addLabelRow(section,
            "<html>Start tracking when the event begins. Stop & Submit when done — "
            + "attendance is sent directly to the backend.</html>", 10f);

        section.add(javax.swing.Box.createVerticalStrut(6));

        // Event name row
        JPanel nameRow = new JPanel(new GridBagLayout());
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        nameRow.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.insets = new Insets(0, 0, 0, 4);

        JLabel nameLabel = new JLabel("Event name:");
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        nameLabel.setFont(nameLabel.getFont().deriveFont(11f));
        c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST;
        nameRow.add(nameLabel, c);

        eventNameField = new JTextField();
        eventNameField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventNameField.setForeground(Color.WHITE);
        eventNameField.setCaretColor(Color.WHITE);
        eventNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(2, 4, 2, 4)
        ));
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        nameRow.add(eventNameField, c);

        section.add(nameRow);
        section.add(javax.swing.Box.createVerticalStrut(4));

        // Threshold row — "Min. time (min): [spinner]"
        JPanel thresholdRow = new JPanel(new GridBagLayout());
        thresholdRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        thresholdRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        thresholdRow.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints tc = new GridBagConstraints();
        tc.gridy = 0; tc.insets = new Insets(0, 0, 0, 4);

        JLabel thresholdLabel = new JLabel("Min. time (min):");
        thresholdLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        thresholdLabel.setFont(thresholdLabel.getFont().deriveFont(11f));
        tc.gridx = 0; tc.weightx = 1.0; tc.fill = GridBagConstraints.HORIZONTAL;
        tc.anchor = GridBagConstraints.WEST;
        thresholdRow.add(thresholdLabel, tc);

        // default 10 min, range 1–120
        thresholdSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 120, 1));
        thresholdSpinner.setPreferredSize(new Dimension(52, 22));
        tc.gridx = 1; tc.weightx = 0; tc.fill = GridBagConstraints.NONE;
        tc.insets = new Insets(0, 0, 0, 0);
        thresholdRow.add(thresholdSpinner, tc);

        section.add(thresholdRow);
        section.add(javax.swing.Box.createVerticalStrut(6));

        // Live status label
        attendanceStatusLabel = new JLabel("No event running");
        attendanceStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        attendanceStatusLabel.setFont(attendanceStatusLabel.getFont().deriveFont(10f));
        addLabelComponent(section, attendanceStatusLabel);

        section.add(javax.swing.Box.createVerticalStrut(4));

        // Start button
        startEventButton = makeButton("Start Event");
        startEventButton.addActionListener(e -> startAttendanceEvent());
        section.add(startEventButton);

        section.add(javax.swing.Box.createVerticalStrut(4));

        // Stop & Submit button — disabled until event is running
        stopSubmitButton = makeButton("Stop & Submit");
        stopSubmitButton.setEnabled(false);
        stopSubmitButton.addActionListener(e -> stopAndSubmitAttendance());
        section.add(stopSubmitButton);

        section.add(javax.swing.Box.createVerticalStrut(2));

        attendanceResultLabel = new JLabel(" ");
        attendanceResultLabel.setFont(attendanceResultLabel.getFont().deriveFont(10f));
        addLabelComponent(section, attendanceResultLabel);

        return section;
    }

    private void startAttendanceEvent() {
        if (attendanceTracker == null) return;
        attendanceTracker.startEvent();
        startEventButton.setEnabled(false);
        stopSubmitButton.setEnabled(true);
        attendanceStatusLabel.setText("Event running — tracking members...");
        attendanceStatusLabel.setForeground(new Color(0x4CAF50));
        attendanceResultLabel.setText(" ");

        // Tick a live counter every second
        Timer liveTimer = new Timer(1000, null);
        liveTimer.addActionListener(e -> {
            if (attendanceTracker == null || !attendanceTracker.isRunning()) {
                liveTimer.stop();
                return;
            }
            int secs = attendanceTracker.getEventDurationSeconds();
            int mins = secs / 60;
            attendanceStatusLabel.setText(String.format(
                "Running %02d:%02d — %d members seen",
                mins, secs % 60, attendanceTracker.getMemberCount()
            ));
        });
        liveTimer.start();
    }

    private void stopAndSubmitAttendance() {
        if (attendanceTracker == null || adminApi == null) return;

        String eventName = eventNameField.getText().trim();
        int thresholdSeconds = (int) thresholdSpinner.getValue() * 60;
        java.util.List<AttendanceEntry> entries = attendanceTracker.stopEvent(thresholdSeconds, thresholdSeconds / 2);
        int duration = attendanceTracker.getEventDurationSeconds();

        startEventButton.setEnabled(true);
        stopSubmitButton.setEnabled(false);
        attendanceStatusLabel.setText("Submitting " + entries.size() + " entries...");
        attendanceStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        adminApi.submitAttendance(
            eventName.isEmpty() ? "Unnamed Event" : eventName,
            duration,
            entries,
            result -> SwingUtilities.invokeLater(() -> {
                if (result.isSuccess()) {
                    attendanceStatusLabel.setText("Done — event ended");
                    attendanceStatusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    attendanceResultLabel.setText(String.format(
                        "Submitted %d, matched %d, %d pts each",
                        result.getTotalSubmitted(), result.getMatched(), result.getPointsAwarded()
                    ));
                    attendanceResultLabel.setForeground(new Color(0x4CAF50));
                    if (result.getUnmatched() != null && !result.getUnmatched().isEmpty()) {
                        JOptionPane.showMessageDialog(this,
                            "Unmatched RSNs (not in backend):\n\n"
                                + String.join("\n", result.getUnmatched()),
                            "Unmatched Players", JOptionPane.INFORMATION_MESSAGE);
                    }
                    eventNameField.setText("");
                } else {
                    attendanceResultLabel.setText("Submission failed");
                    attendanceResultLabel.setForeground(new Color(0xFF5252));
                }
            }),
            error -> SwingUtilities.invokeLater(() -> {
                attendanceResultLabel.setText(error instanceof SecurityException
                    ? "Access denied" : "Error: " + error.getMessage());
                attendanceResultLabel.setForeground(new Color(0xFF5252));
            })
        );
    }

    // =========================================================================
    // Section 2: Rank Changes
    // =========================================================================

    private JPanel buildRankChangesSection() {
        JPanel section = createSection("Pending Rank Changes");

        // Propose form
        JPanel proposeForm = new JPanel(new GridBagLayout());
        proposeForm.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        proposeForm.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        proposeForm.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 2, 4);

        // RSN field
        JLabel rsnLbl = new JLabel("RSN:");
        rsnLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rsnLbl.setFont(rsnLbl.getFont().deriveFont(11f));
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        proposeForm.add(rsnLbl, c);

        JTextField proposeRsnField = new JTextField();
        proposeRsnField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        proposeRsnField.setForeground(Color.WHITE);
        proposeRsnField.setCaretColor(Color.WHITE);
        proposeRsnField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(2, 4, 2, 4)));
        c.gridx = 1; c.weightx = 1.0; c.insets = new Insets(2, 0, 2, 0);
        proposeForm.add(proposeRsnField, c);

        // New rank field
        JLabel rankLbl = new JLabel("New rank:");
        rankLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rankLbl.setFont(rankLbl.getFont().deriveFont(11f));
        c.gridx = 0; c.gridy = 1; c.weightx = 0; c.insets = new Insets(2, 0, 2, 4);
        proposeForm.add(rankLbl, c);

        JTextField proposeRankField = new JTextField();
        proposeRankField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        proposeRankField.setForeground(Color.WHITE);
        proposeRankField.setCaretColor(Color.WHITE);
        proposeRankField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(2, 4, 2, 4)));
        c.gridx = 1; c.weightx = 1.0; c.insets = new Insets(2, 0, 2, 0);
        proposeForm.add(proposeRankField, c);

        // Reason field
        JLabel reasonLbl = new JLabel("Reason:");
        reasonLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        reasonLbl.setFont(reasonLbl.getFont().deriveFont(11f));
        c.gridx = 0; c.gridy = 2; c.weightx = 0; c.insets = new Insets(2, 0, 2, 4);
        proposeForm.add(reasonLbl, c);

        JTextField proposeReasonField = new JTextField();
        proposeReasonField.setBackground(ColorScheme.DARK_GRAY_COLOR);
        proposeReasonField.setForeground(Color.WHITE);
        proposeReasonField.setCaretColor(Color.WHITE);
        proposeReasonField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(2, 4, 2, 4)));
        c.gridx = 1; c.weightx = 1.0; c.insets = new Insets(2, 0, 2, 0);
        proposeForm.add(proposeReasonField, c);

        section.add(proposeForm);
        section.add(javax.swing.Box.createVerticalStrut(4));

        JLabel proposeResultLabel = new JLabel(" ");
        proposeResultLabel.setFont(proposeResultLabel.getFont().deriveFont(10f));
        addLabelComponent(section, proposeResultLabel);

        JButton proposeBtn = makeButton("Propose Rank Change");
        proposeBtn.addActionListener(e -> {
            String rsn = proposeRsnField.getText().trim();
            String rank = proposeRankField.getText().trim();
            String reason = proposeReasonField.getText().trim();
            if (rsn.isEmpty() || rank.isEmpty()) {
                proposeResultLabel.setText("RSN and rank are required");
                proposeResultLabel.setForeground(new Color(0xFF5252));
                return;
            }
            proposeBtn.setEnabled(false);
            proposeResultLabel.setText("Proposing...");
            proposeResultLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            adminApi.proposeRankChange(rsn, rank, reason,
                result -> SwingUtilities.invokeLater(() -> {
                    proposeBtn.setEnabled(true);
                    proposeResultLabel.setText(result.getMemberRsn() + ": " + result.getOldRank() + " -> " + result.getNewRank());
                    proposeResultLabel.setForeground(new Color(0x4CAF50));
                    proposeRsnField.setText("");
                    proposeRankField.setText("");
                    proposeReasonField.setText("");
                    refreshRankChanges();
                }),
                error -> SwingUtilities.invokeLater(() -> {
                    proposeBtn.setEnabled(true);
                    proposeResultLabel.setText(error instanceof SecurityException ? "Access denied" : "Failed");
                    proposeResultLabel.setForeground(new Color(0xFF5252));
                })
            );
        });
        section.add(proposeBtn);

        section.add(javax.swing.Box.createVerticalStrut(8));

        // Pending list
        rankChangesContainer = new JPanel();
        rankChangesContainer.setLayout(new BoxLayout(rankChangesContainer, BoxLayout.Y_AXIS));
        rankChangesContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rankChangesContainer.setAlignmentX(LEFT_ALIGNMENT);
        rankChangesContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel emptyLabel = new JLabel("Click refresh to load");
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rankChangesContainer.add(emptyLabel);
        section.add(rankChangesContainer);

        section.add(javax.swing.Box.createVerticalStrut(4));

        refreshRankChangesButton = makeButton("Refresh");
        refreshRankChangesButton.addActionListener(e -> refreshRankChanges());
        section.add(refreshRankChangesButton);

        return section;
    }

    private void refreshRankChanges() {
        refreshRankChangesButton.setEnabled(false);
        adminApi.fetchPendingRankChanges(
            changes -> SwingUtilities.invokeLater(() -> {
                refreshRankChangesButton.setEnabled(true);
                updateRankChangesList(changes);
            }),
            error -> SwingUtilities.invokeLater(() -> {
                refreshRankChangesButton.setEnabled(true);
                rankChangesContainer.removeAll();
                JLabel errLabel = new JLabel(error instanceof SecurityException ? "Access denied" : "Failed to load");
                errLabel.setForeground(new Color(0xFF5252));
                rankChangesContainer.add(errLabel);
                rankChangesContainer.revalidate();
            })
        );
    }

    private void updateRankChangesList(List<RankChange> changes) {
        rankChangesContainer.removeAll();
        if (changes == null || changes.isEmpty()) {
            JLabel empty = new JLabel("No pending rank changes");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            rankChangesContainer.add(empty);
        } else {
            for (RankChange change : changes) {
                rankChangesContainer.add(createRankChangeCard(change));
            }
        }
        rankChangesContainer.revalidate();
        rankChangesContainer.repaint();
    }

    private JPanel createRankChangeCard(RankChange change) {
        // Rule 8: GridBagLayout for card with right-side action button
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(ColorScheme.DARK_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 6, 6, 6)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();

        // Left info column
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel rsnLabel = new JLabel(change.getMemberRsn());
        rsnLabel.setForeground(Color.WHITE);
        rsnLabel.setFont(rsnLabel.getFont().deriveFont(Font.BOLD, 12f));
        rsnLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, rsnLabel.getPreferredSize().height));
        info.add(rsnLabel);

        JLabel rankLabel = new JLabel(change.getOldRank() + " -> " + change.getNewRank());
        rankLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rankLabel.setFont(rankLabel.getFont().deriveFont(10f));
        rankLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, rankLabel.getPreferredSize().height));
        info.add(rankLabel);

        if (change.getRequestedBy() != null && !change.getRequestedBy().isEmpty()) {
            JLabel byLabel = new JLabel("by " + change.getRequestedBy());
            byLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            byLabel.setFont(byLabel.getFont().deriveFont(10f));
            byLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, byLabel.getPreferredSize().height));
            info.add(byLabel);
        }

        c.gridx = 0; c.gridy = 0; c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 4);
        card.add(info, c);

        // Right: single "Mark Done" button
        JButton doneBtn = new JButton("Mark Done");
        doneBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        doneBtn.addActionListener(e -> {
            doneBtn.setEnabled(false);
            adminApi.actualizeRankChange(change.getId(),
                success -> SwingUtilities.invokeLater(this::refreshRankChanges),
                error -> SwingUtilities.invokeLater(() -> {
                    doneBtn.setEnabled(true);
                    log.warn("Failed to actualize rank change {}", change.getId(), error);
                })
            );
        });

        c.gridx = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER; c.insets = new Insets(0, 0, 0, 0);
        card.add(doneBtn, c);

        return card;
    }

    // =========================================================================
    // Section 3: Announcements
    // =========================================================================

    private JPanel buildAnnouncementSection() {
        JPanel section = createSection("Announcement");

        addLabelRow(section, "<html>Set the message shown to all clan members on login.</html>", 10f);
        section.add(javax.swing.Box.createVerticalStrut(4));

        announcementTextArea = new JTextArea(3, 0);
        announcementTextArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
        announcementTextArea.setForeground(Color.WHITE);
        announcementTextArea.setCaretColor(Color.WHITE);
        announcementTextArea.setLineWrap(true);
        announcementTextArea.setWrapStyleWord(true);
        announcementTextArea.setBorder(new EmptyBorder(4, 4, 4, 4));

        JScrollPane textScroll = new JScrollPane(announcementTextArea);
        textScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        textScroll.setAlignmentX(LEFT_ALIGNMENT);
        textScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        section.add(textScroll);

        section.add(javax.swing.Box.createVerticalStrut(4));

        updateAnnouncementButton = makeButton("Update");
        updateAnnouncementButton.addActionListener(e -> updateAnnouncement());
        section.add(updateAnnouncementButton);

        section.add(javax.swing.Box.createVerticalStrut(2));

        announcementResultLabel = new JLabel(" ");
        announcementResultLabel.setFont(announcementResultLabel.getFont().deriveFont(10f));
        addLabelComponent(section, announcementResultLabel);

        return section;
    }

    private void updateAnnouncement() {
        String message = announcementTextArea.getText().trim();
        updateAnnouncementButton.setEnabled(false);
        announcementResultLabel.setText("Updating...");
        announcementResultLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        adminApi.updateAnnouncement(message,
            success -> SwingUtilities.invokeLater(() -> {
                updateAnnouncementButton.setEnabled(true);
                announcementResultLabel.setText("Announcement updated");
                announcementResultLabel.setForeground(new Color(0x4CAF50));
            }),
            error -> SwingUtilities.invokeLater(() -> {
                updateAnnouncementButton.setEnabled(true);
                announcementResultLabel.setText(error instanceof SecurityException
                    ? "Access denied" : "Failed: " + error.getMessage());
                announcementResultLabel.setForeground(new Color(0xFF5252));
            })
        );
    }

    // =========================================================================
    // Section 4: Roster Sync
    // =========================================================================

    private JPanel buildRosterSyncSection() {
        JPanel section = createSection("Roster Sync");

        addLabelRow(section,
            "<html>Push the current in-game clan roster to the backend. "
            + "Uses add_only mode — existing members are not removed.</html>", 10f);
        section.add(javax.swing.Box.createVerticalStrut(6));

        syncRosterButton = makeButton("Sync Roster");
        syncRosterButton.addActionListener(e -> {
            if (onGroupSync == null) return;
            syncRosterButton.setEnabled(false);
            syncRosterResultLabel.setText("Syncing...");
            syncRosterResultLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            onGroupSync.run();
            // Safety: re-enable after 5s in case the callback never fires
            Timer timeout = new Timer(5_000, ev -> {
                syncRosterButton.setEnabled(true);
                syncRosterResultLabel.setText("Done — check logs for details");
                syncRosterResultLabel.setForeground(new Color(0x4CAF50));
            });
            timeout.setRepeats(false);
            timeout.start();
        });
        section.add(syncRosterButton);

        section.add(javax.swing.Box.createVerticalStrut(2));

        syncRosterResultLabel = new JLabel(" ");
        syncRosterResultLabel.setFont(syncRosterResultLabel.getFont().deriveFont(10f));
        addLabelComponent(section, syncRosterResultLabel);

        return section;
    }

    // =========================================================================
    // Layout helpers
    // =========================================================================

    /**
     * Creates a section panel (BoxLayout Y_AXIS) with a bold header label.
     * Fills full width, no fixed height cap.
     */
    private JPanel createSection(String title) {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(8, 8, 8, 8)
        ));
        section.setAlignmentX(LEFT_ALIGNMENT);
        // Rule 4: cap width so BoxLayout parent never stretches us wider than the viewport
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel header = new JLabel(title);
        header.setForeground(Color.WHITE);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setAlignmentX(LEFT_ALIGNMENT);
        header.setBorder(new EmptyBorder(0, 0, 6, 0));
        section.add(header);

        return section;
    }

    /**
     * Rule 10: Wrap a JLabel in a single-row GridBagLayout panel so BoxLayout
     * respects its preferred height and it fills the full available width.
     */
    private void addLabelComponent(JPanel parent, JLabel label) {
        label.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(label);
    }

    /**
     * Adds an HTML instruction label to a section panel.
     */
    private void addLabelRow(JPanel parent, String html, float fontSize) {
        JLabel label = new JLabel(html);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(label.getFont().deriveFont(fontSize));
        label.setAlignmentX(LEFT_ALIGNMENT);
        // Rule 4: must constrain width so BoxLayout doesn't let it overflow
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        parent.add(label);
    }

    /**
     * Rule 4: Creates a full-width button that stretches to fill available width.
     */
    private JButton makeButton(String text) {
        JButton btn = new JButton(text);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, btn.getPreferredSize().height));
        return btn;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public void setCurrentAnnouncement(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message != null) announcementTextArea.setText(message);
        });
    }

    public void setAttendanceTracker(EventAttendanceTracker tracker, AdminApiService api) {
        this.attendanceTracker = tracker;
        // Start button only visible when tracker is wired (i.e. plugin is running)
        if (startEventButton != null) startEventButton.setEnabled(true);
    }

    // =========================================================================
    // Scrollable — forces JScrollPane to always size us to viewport width.
    // Same fix as ClanHubPanel: prevents any child's preferred width from
    // causing horizontal overflow.
    // =========================================================================

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 16; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
