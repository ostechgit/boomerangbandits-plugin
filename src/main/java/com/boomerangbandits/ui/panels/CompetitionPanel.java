package com.boomerangbandits.ui.panels;

import com.boomerangbandits.BoomerangBanditsConfig;
import com.boomerangbandits.api.ClanContentService;
import com.boomerangbandits.api.WomApiService;
import com.boomerangbandits.api.models.ActiveEvent;
import com.boomerangbandits.api.models.EventDetails;
import com.boomerangbandits.api.models.WomCompetition;
import com.boomerangbandits.api.models.WomParticipant;
import com.boomerangbandits.ui.UIConstants;
import com.boomerangbandits.ui.components.CountdownLabel;
import com.boomerangbandits.ui.components.LeaderboardTable;
import com.boomerangbandits.util.RefreshThrottler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.ColorScheme;

/**
 * Competition panel — list of WOM competitions + active/upcoming clan events.
 *
 * Uses show/hide instead of CardLayout to avoid height-retention bug.
 */
@Slf4j
public class CompetitionPanel extends JPanel {

    private final WomApiService womApi;
    private final ClanContentService contentService;
    private final BoomerangBanditsConfig config;
    private final Client client;
    private final Gson gson;

    private static final ZoneId SYDNEY = ZoneId.of("Australia/Sydney");
    private static final DateTimeFormatter INPUT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("h:mma zzz dd/MM");

    // List view
    private JPanel listPanel;
    private JPanel competitionListContainer;
    private JPanel eventCardContainer;
    private JPanel addEventForm;

    // Detail view
    private JPanel detailPanel;
    private JLabel detailTitle;
    private JLabel detailMetric;
    private CountdownLabel detailCountdown;
    private LeaderboardTable detailLeaderboard;

    // Add-event form fields
    private JTextField fieldName;
    private JTextField fieldDescription;
    private JComboBox<String> fieldEventType;
    private JTextField fieldLocation;
    private JTextField fieldWorld;
    private JTextField fieldPassword;
    private JTextField fieldChallengePassword;
    private JTextField fieldStartTime;
    private JTextField fieldEndTime;
    private JLabel formStatus;

    private final RefreshThrottler refreshThrottler;

    /** Validated event form fields (world + time range). */
    private static class EventTimeValidation {
        final int world;
        final Instant startUtc;
        final Instant endUtc;

        EventTimeValidation(int world, Instant startUtc, Instant endUtc) {
            this.world = world;
            this.startUtc = startUtc;
            this.endUtc = endUtc;
        }
    }

    /**
     * Validate world number and start/end date strings.
     *
     * @return validated result, or null if validation failed (error already shown via errorHandler)
     */
    private static EventTimeValidation validateWorldAndDates(
            String worldStr, String startStr, String endStr, Consumer<String> errorHandler) {
        int world;
        try {
            world = Integer.parseInt(worldStr);
            if (world < 1 || world > 999) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            errorHandler.accept("World must be 1-999");
            return null;
        }

        Instant startUtc, endUtc;
        try {
            startUtc = LocalDateTime.parse(startStr, INPUT_FMT).atZone(SYDNEY).toInstant();
            endUtc = LocalDateTime.parse(endStr, INPUT_FMT).atZone(SYDNEY).toInstant();
        } catch (DateTimeParseException e) {
            errorHandler.accept("Use format: dd/MM/yyyy HH:mm");
            return null;
        }

        if (!endUtc.isAfter(startUtc)) {
            errorHandler.accept("End must be after start");
            return null;
        }

        return new EventTimeValidation(world, startUtc, endUtc);
    }

    @Inject
    public CompetitionPanel(WomApiService womApi, ClanContentService contentService,
                            BoomerangBanditsConfig config, Client client, Gson gson) {
        this.womApi = womApi;
        this.contentService = contentService;
        this.config = config;
        this.client = client;
        this.gson = gson;
        this.refreshThrottler = new RefreshThrottler(60 * 60 * 1_000); // 1 hour

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        buildListPanel();
        buildDetailPanel();

        add(listPanel);
        add(detailPanel);

        showList();
    }

    // -------------------------------------------------------------------------
    // Build — list panel
    // -------------------------------------------------------------------------

    private void buildListPanel() {
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listPanel.setAlignmentX(LEFT_ALIGNMENT);

        JLabel compHeader = new JLabel("Competitions");
        compHeader.setForeground(Color.WHITE);
        compHeader.setFont(UIConstants.deriveFont(compHeader.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        compHeader.setBorder(new EmptyBorder(UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD));
        compHeader.setAlignmentX(LEFT_ALIGNMENT);
        listPanel.add(compHeader);

        competitionListContainer = new JPanel();
        competitionListContainer.setLayout(new BoxLayout(competitionListContainer, BoxLayout.Y_AXIS));
        competitionListContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        competitionListContainer.setAlignmentX(LEFT_ALIGNMENT);
        listPanel.add(competitionListContainer);

        JLabel eventHeader = new JLabel("Events");
        eventHeader.setForeground(Color.WHITE);
        eventHeader.setFont(UIConstants.deriveFont(eventHeader.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        eventHeader.setBorder(new EmptyBorder(UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_SMALL, UIConstants.PADDING_STANDARD));
        eventHeader.setAlignmentX(LEFT_ALIGNMENT);
        listPanel.add(eventHeader);

        eventCardContainer = new JPanel();
        eventCardContainer.setLayout(new BoxLayout(eventCardContainer, BoxLayout.Y_AXIS));
        eventCardContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventCardContainer.setAlignmentX(LEFT_ALIGNMENT);
        listPanel.add(eventCardContainer);

        addEventForm = buildAddEventForm();
        listPanel.add(addEventForm);
    }

    // -------------------------------------------------------------------------
    // Build — add event form (collapsible)
    // -------------------------------------------------------------------------

    private JPanel buildAddEventForm() {
        // Outer wrapper — hidden until authenticated
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(ColorScheme.DARK_GRAY_COLOR);
        form.setAlignmentX(LEFT_ALIGNMENT);
        form.setBorder(new EmptyBorder(UIConstants.PADDING_SMALL, UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD));

        // --- Clickable header row (GridBagLayout: title left, arrow right) ---
        JPanel headerRow = new JPanel(new GridBagLayout());
        headerRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerRow.setBorder(new EmptyBorder(UIConstants.PADDING_SMALL, UIConstants.PADDING_SMALL,
            UIConstants.PADDING_SMALL, UIConstants.PADDING_SMALL));
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        headerRow.setAlignmentX(LEFT_ALIGNMENT);
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel headerLabel = new JLabel("Add Event");
        headerLabel.setForeground(Color.WHITE);
        headerLabel.setFont(UIConstants.deriveFont(headerLabel.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));

        JLabel arrowLabel = new JLabel("v");
        arrowLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        arrowLabel.setFont(UIConstants.deriveFont(arrowLabel.getFont(), UIConstants.FONT_SIZE_SMALL));

        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 0; hc.gridy = 0; hc.weightx = 1.0;
        hc.fill = GridBagConstraints.HORIZONTAL; hc.anchor = GridBagConstraints.WEST;
        headerRow.add(headerLabel, hc);

        hc.gridx = 1; hc.weightx = 0;
        hc.fill = GridBagConstraints.NONE; hc.anchor = GridBagConstraints.EAST;
        headerRow.add(arrowLabel, hc);

        form.add(headerRow);

        // --- Collapsible body (collapsed by default) ---
        JPanel formBody = new JPanel();
        formBody.setLayout(new BoxLayout(formBody, BoxLayout.Y_AXIS));
        formBody.setBackground(ColorScheme.DARK_GRAY_COLOR);
        formBody.setAlignmentX(LEFT_ALIGNMENT);
        formBody.setBorder(new EmptyBorder(UIConstants.PADDING_SMALL, 0, 0, 0));
        formBody.setVisible(false);

        fieldName = addFormRow(formBody, "Event Name");
        fieldDescription = addFormRow(formBody, "Description");

        JLabel typeLabel = new JLabel("Event Type");
        typeLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        typeLabel.setAlignmentX(LEFT_ALIGNMENT);
        formBody.add(typeLabel);
        fieldEventType = new JComboBox<>(new String[]{"PVM", "PVP", "RAID", "PARTY", "MINI", "LEVEL", "SKILL"});
        fieldEventType.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        fieldEventType.setAlignmentX(LEFT_ALIGNMENT);
        fieldEventType.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        fieldEventType.setForeground(Color.WHITE);
        formBody.add(fieldEventType);
        formBody.add(javax.swing.Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        fieldLocation = addFormRow(formBody, "Location");
        fieldWorld = addFormRow(formBody, "World (e.g. 416)");
        fieldPassword = addFormRow(formBody, "Password (optional)");
        fieldChallengePassword = addFormRow(formBody, "Challenge PW (optional)");
        fieldStartTime = addFormRow(formBody, "Start (dd/MM/yyyy HH:mm)");
        fieldEndTime = addFormRow(formBody, "End (dd/MM/yyyy HH:mm)");

        formStatus = new JLabel(" ");
        formStatus.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        formStatus.setAlignmentX(LEFT_ALIGNMENT);
        formBody.add(formStatus);
        formBody.add(javax.swing.Box.createVerticalStrut(UIConstants.SPACING_SMALL));

        JButton submitBtn = new JButton("Create Event");
        submitBtn.setAlignmentX(LEFT_ALIGNMENT);
        submitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        submitBtn.addActionListener(e -> submitEvent());
        formBody.add(submitBtn);

        form.add(formBody);

        // Toggle on header click
        headerRow.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                boolean nowVisible = !formBody.isVisible();
                formBody.setVisible(nowVisible);
                arrowLabel.setText(nowVisible ? "v" : ">");
                listPanel.revalidate();
                listPanel.repaint();
            }
        });

        form.setVisible(false);
        return form;
    }

    private JTextField addFormRow(JPanel parent, String placeholder) {
        JTextField field = new JTextField();
        field.setToolTipText(placeholder);
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        field.setAlignmentX(LEFT_ALIGNMENT);
        field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);

        JLabel label = new JLabel(placeholder);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(label);
        parent.add(field);
        parent.add(javax.swing.Box.createVerticalStrut(UIConstants.SPACING_SMALL));
        return field;
    }

    private void submitEvent() {
        String memberCode = config.memberCode();
        if (memberCode == null || memberCode.isEmpty()) {
            setFormStatus("Not authenticated", Color.RED);
            return;
        }

        String name = fieldName.getText().trim();
        String description = fieldDescription.getText().trim();
        String location = fieldLocation.getText().trim();
        String worldStr = fieldWorld.getText().trim();
        String password = fieldPassword.getText().trim();
        String challengePw = fieldChallengePassword.getText().trim();
        String startStr = fieldStartTime.getText().trim();
        String endStr = fieldEndTime.getText().trim();

        if (name.isEmpty() || description.isEmpty() || location.isEmpty() || worldStr.isEmpty()
                || startStr.isEmpty() || endStr.isEmpty()) {
            setFormStatus("Fill in all required fields", Color.RED);
            return;
        }

        EventTimeValidation v = validateWorldAndDates(worldStr, startStr, endStr,
            msg -> setFormStatus(msg, Color.RED));
        if (v == null) return;

        String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";

        JsonObject body = new JsonObject();
        body.addProperty("eventName", name);
        body.addProperty("eventType", (String) fieldEventType.getSelectedItem());
        body.addProperty("description", description);
        body.addProperty("location", location);
        body.addProperty("world", v.world);
        body.addProperty("organiserRsn", rsn);
        body.addProperty("startTime", v.startUtc.toString());
        body.addProperty("endTime", v.endUtc.toString());
        if (!password.isEmpty()) body.addProperty("eventPassword", password);
        if (!challengePw.isEmpty()) body.addProperty("challengePassword", challengePw);

        setFormStatus("Submitting...", ColorScheme.LIGHT_GRAY_COLOR);

        contentService.createEvent(memberCode, gson.toJson(body),
            () -> SwingUtilities.invokeLater(() -> {
                setFormStatus("Event created!", new Color(0x4CAF50));
                clearForm();
                forceRefresh();
            }),
            err -> SwingUtilities.invokeLater(() ->
                setFormStatus("Failed: " + err.getMessage(), Color.RED))
        );
    }

    private void setFormStatus(String msg, Color color) {
        formStatus.setText(msg);
        formStatus.setForeground(color);
    }

    private void clearForm() {
        fieldName.setText("");
        fieldDescription.setText("");
        fieldEventType.setSelectedIndex(0);
        fieldLocation.setText("");
        fieldWorld.setText("");
        fieldPassword.setText("");
        fieldChallengePassword.setText("");
        fieldStartTime.setText("");
        fieldEndTime.setText("");
    }

    // -------------------------------------------------------------------------
    // Build — detail panel
    // -------------------------------------------------------------------------

    private void buildDetailPanel() {
        detailPanel = new JPanel();
        detailPanel.setLayout(new BoxLayout(detailPanel, BoxLayout.Y_AXIS));
        detailPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        detailPanel.setAlignmentX(LEFT_ALIGNMENT);

        JButton backButton = new JButton("< Back");
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.addActionListener(e -> showList());
        backButton.setAlignmentX(LEFT_ALIGNMENT);
        detailPanel.add(backButton);

        JPanel detailHeader = new JPanel();
        detailHeader.setLayout(new BoxLayout(detailHeader, BoxLayout.Y_AXIS));
        detailHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
        detailHeader.setBorder(new EmptyBorder(UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD));
        detailHeader.setAlignmentX(LEFT_ALIGNMENT);

        detailTitle = new JLabel("Competition");
        detailTitle.setForeground(Color.WHITE);
        detailTitle.setFont(UIConstants.deriveFont(detailTitle.getFont(), UIConstants.FONT_SIZE_LARGE, UIConstants.FONT_BOLD));
        detailTitle.setAlignmentX(LEFT_ALIGNMENT);
        detailHeader.add(detailTitle);

        detailMetric = new JLabel("Metric: --");
        detailMetric.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        detailMetric.setAlignmentX(LEFT_ALIGNMENT);
        detailHeader.add(detailMetric);

        detailCountdown = new CountdownLabel("Ends in: ");
        detailCountdown.setAlignmentX(LEFT_ALIGNMENT);
        detailHeader.add(detailCountdown);

        detailPanel.add(detailHeader);

        detailLeaderboard = new LeaderboardTable("Player", "Gained");
        detailLeaderboard.setAlignmentX(LEFT_ALIGNMENT);
        detailPanel.add(detailLeaderboard);
    }

    // -------------------------------------------------------------------------
    // View switching
    // -------------------------------------------------------------------------

    private void showList() {
        detailPanel.setVisible(false);
        listPanel.setVisible(true);
        revalidate();
        repaint();
    }

    private void showDetail() {
        listPanel.setVisible(false);
        detailPanel.setVisible(true);
        revalidate();
        repaint();
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    public void refresh() {
        boolean authenticated = config.memberCode() != null && !config.memberCode().isEmpty();
        SwingUtilities.invokeLater(() -> addEventForm.setVisible(authenticated));

        if (!refreshThrottler.shouldRefresh()) {
            return;
        }
        refreshThrottler.recordRefresh();

        womApi.fetchCompetitions(
            competitions -> SwingUtilities.invokeLater(() -> updateCompetitionList(competitions)),
            error -> SwingUtilities.invokeLater(() -> {
                log.warn("[CompetitionPanel] Failed to load competitions", error);
                competitionListContainer.removeAll();
                JLabel err = new JLabel("Failed to load competitions");
                err.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                err.setBorder(new EmptyBorder(8, UIConstants.PADDING_STANDARD, 8, UIConstants.PADDING_STANDARD));
                competitionListContainer.add(err);
                competitionListContainer.revalidate();
            })
        );

        contentService.fetchActiveEvent(
            event -> SwingUtilities.invokeLater(() -> updateEventCard(event)),
            error -> log.warn("[CompetitionPanel] Failed to load events", error)
        );
    }

    public void forceRefresh() {
        refreshThrottler.reset();
        contentService.invalidateCaches();
        refresh();
    }

    // -------------------------------------------------------------------------
    // Competition list
    // -------------------------------------------------------------------------

    private void updateCompetitionList(List<WomCompetition> competitions) {
        competitionListContainer.removeAll();

        if (competitions == null || competitions.isEmpty()) {
            JLabel empty = new JLabel("No competitions found");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setBorder(new EmptyBorder(8, UIConstants.PADDING_STANDARD, 8, UIConstants.PADDING_STANDARD));
            competitionListContainer.add(empty);
        } else {
            for (WomCompetition comp : competitions) {
                if (comp.isOngoing() || comp.isUpcoming()) {
                    competitionListContainer.add(buildCompetitionCard(comp));
                }
            }
        }

        competitionListContainer.revalidate();
        competitionListContainer.repaint();
    }

    private JPanel buildCompetitionCard(WomCompetition comp) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(new EmptyBorder(UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
            UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        card.setAlignmentX(LEFT_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        GridBagConstraints c = new GridBagConstraints();

        JLabel title = new JLabel(comp.getTitle());
        title.setForeground(Color.WHITE);
        title.setFont(UIConstants.deriveFont(title.getFont(), UIConstants.FONT_SIZE_MEDIUM, UIConstants.FONT_BOLD));
        c.gridx = 0; c.gridy = 0; c.weightx = 1.0; c.gridheight = 1;
        c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        c.insets = new java.awt.Insets(0, 0, 2, 0);
        card.add(title, c);

        JLabel arrow = new JLabel("›");
        arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        arrow.setFont(UIConstants.deriveFont(arrow.getFont(), UIConstants.FONT_SIZE_LARGE));
        c.gridx = 1; c.gridy = 0; c.gridheight = 2; c.weightx = 0;
        c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.CENTER;
        c.insets = new java.awt.Insets(0, UIConstants.PADDING_STANDARD, 0, 0);
        card.add(arrow, c);

        String statusText = comp.isOngoing() ? "Ongoing" : "Upcoming";
        Color statusColor = comp.isOngoing() ? new Color(0x4CAF50) : new Color(0xFFC107);
        JLabel status = new JLabel(statusText + " | " + UIConstants.capitalizeLower(comp.getMetric()));
        status.setForeground(statusColor);
        status.setFont(UIConstants.deriveFont(status.getFont(), UIConstants.FONT_SIZE_SMALL));
        c.gridx = 0; c.gridy = 1; c.gridheight = 1; c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        card.add(status, c);

        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                loadCompetitionDetail(comp.getId());
            }
        });

        return card;
    }

    // -------------------------------------------------------------------------
    // Competition detail
    // -------------------------------------------------------------------------

    private void loadCompetitionDetail(int competitionId) {
        showDetail();
        detailTitle.setText("Loading...");
        detailLeaderboard.setData(null);

        womApi.fetchCompetitionDetails(competitionId,
            comp -> SwingUtilities.invokeLater(() -> {
                detailTitle.setText(comp.getTitle());
                detailMetric.setText("Metric: " + UIConstants.capitalizeLower(comp.getMetric()));
                if (comp.isOngoing()) {
                    detailCountdown.setTarget(comp.getEndsAt());
                } else {
                    detailCountdown.stop();
                }

                List<String[]> rows = new ArrayList<>();
                if (comp.getParticipations() != null) {
                    comp.getParticipations().stream()
                        .filter(p -> p.getGained() > 0)
                        .sorted((a, b) -> Long.compare(b.getGained(), a.getGained()))
                        .forEach(p -> rows.add(new String[]{
                            String.valueOf(rows.size() + 1),
                            p.getDisplayName(),
                            String.format("%,d", p.getGained())
                        }));
                }
                detailLeaderboard.setData(rows);
            }),
            error -> SwingUtilities.invokeLater(() -> detailTitle.setText("Failed to load"))
        );
    }

    // -------------------------------------------------------------------------
    // Event cards
    // -------------------------------------------------------------------------

    private void updateEventCard(ActiveEvent activeEvent) {
        eventCardContainer.removeAll();

        List<EventDetails> events = activeEvent != null ? activeEvent.getEvents() : null;
        if (events == null || events.isEmpty()) {
            JLabel none = new JLabel("No events");
            none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            none.setBorder(new EmptyBorder(4, UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD));
            none.setAlignmentX(LEFT_ALIGNMENT);
            eventCardContainer.add(none);
        } else {
            for (EventDetails event : events) {
                JPanel card = buildEventCard(event);
                card.setAlignmentX(LEFT_ALIGNMENT);
                eventCardContainer.add(card);
                eventCardContainer.add(javax.swing.Box.createVerticalStrut(UIConstants.SPACING_SMALL));
            }
        }

        eventCardContainer.revalidate();
        eventCardContainer.repaint();
    }

    private JPanel buildEventCard(EventDetails event) {
        // Determine live vs upcoming from startTime
        boolean isLive = true;
        if (event.getStartTime() != null && !event.getStartTime().isEmpty()) {
            try {
                isLive = Instant.parse(event.getStartTime()).isBefore(Instant.now());
            } catch (Exception ignored) {}
        }

        Color borderColor = isLive ? new Color(0x4CAF50) : new Color(0xFFC107);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor),
            new EmptyBorder(UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD,
                UIConstants.PADDING_STANDARD, UIConstants.PADDING_STANDARD)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setAlignmentX(LEFT_ALIGNMENT);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new java.awt.Insets(1, 0, 1, 0);
        int row = 0;

        // Name — col 0
        JLabel nameLabel = new JLabel(event.getName());
        nameLabel.setForeground(Color.WHITE);
        c.gridx = 0; c.gridy = row; c.weightx = 1.0; c.gridwidth = 1;
        card.add(nameLabel, c);

        // Type badge — col 1
        if (event.getEventType() != null && !event.getEventType().isEmpty()) {
            JLabel typeLabel = new JLabel(UIConstants.capitalizeLower(event.getEventType()));
            typeLabel.setForeground(new Color(0x2196F3));
            typeLabel.setFont(UIConstants.deriveFont(typeLabel.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
            typeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2196F3)),
                new EmptyBorder(0, 3, 0, 3)
            ));
            c.gridx = 1; c.gridy = row; c.weightx = 0;
            c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
            c.insets = new java.awt.Insets(1, 4, 1, 4);
            card.add(typeLabel, c);
        }

        // LIVE / UPCOMING badge — col 2
        JLabel statusBadge = new JLabel(isLive ? "LIVE" : "UPCOMING");
        statusBadge.setForeground(borderColor);
        statusBadge.setFont(UIConstants.deriveFont(statusBadge.getFont(), UIConstants.FONT_SIZE_SMALL, UIConstants.FONT_BOLD));
        c.gridx = 2; c.gridy = row; c.weightx = 0;
        c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.EAST;
        c.insets = new java.awt.Insets(1, 0, 1, 0);
        card.add(statusBadge, c);
        row++;

        // Full-width rows span all 3 cols
        c.gridx = 0; c.weightx = 1.0; c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.WEST;
        c.insets = new java.awt.Insets(1, 0, 1, 0);

        if (event.getLocation() != null && !event.getLocation().isEmpty()) {
            String loc = event.getLocation() + (event.getWorld() > 0 ? "  W" + event.getWorld() : "");
            c.gridy = row++;
            card.add(makeEventLabel(loc, ColorScheme.LIGHT_GRAY_COLOR), c);
        }

        if (event.getOrganiserRsn() != null && !event.getOrganiserRsn().isEmpty()) {
            c.gridy = row++;
            card.add(makeEventLabel("Host: " + event.getOrganiserRsn(), ColorScheme.LIGHT_GRAY_COLOR), c);
        }

        if (event.getEventPassword() != null && !event.getEventPassword().isEmpty()) {
            c.gridy = row++;
            card.add(makeEventLabel("Password: " + event.getEventPassword(), new Color(0xFFC107)), c);
        }

        if (isLive) {
            // Show end time + countdown to end
            if (event.getEndTime() != null && !event.getEndTime().isEmpty()) {
                try {
                    String formatted = DISPLAY_FMT.withZone(SYDNEY).format(Instant.parse(event.getEndTime()));
                    c.gridy = row++;
                    card.add(makeEventLabel("Ends: " + formatted, ColorScheme.LIGHT_GRAY_COLOR), c);
                } catch (Exception ex) {
                    log.warn("Could not parse endTime: {}", event.getEndTime());
                }
                CountdownLabel countdown = new CountdownLabel("Ends in: ");
                countdown.setTarget(event.getEndTime());
                c.gridy = row++;
                card.add(countdown, c);
            }
        } else {
            // Show start time + countdown to start
            if (event.getStartTime() != null && !event.getStartTime().isEmpty()) {
                try {
                    String formatted = DISPLAY_FMT.withZone(SYDNEY).format(Instant.parse(event.getStartTime()));
                    c.gridy = row++;
                    card.add(makeEventLabel("Starts: " + formatted, ColorScheme.LIGHT_GRAY_COLOR), c);
                } catch (Exception ex) {
                    log.warn("Could not parse startTime: {}", event.getStartTime());
                }
                CountdownLabel countdown = new CountdownLabel("Starts in: ");
                countdown.setTarget(event.getStartTime());
                c.gridy = row++;
                card.add(countdown, c);
            }
        }

        // Edit + Delete buttons — only for the organiser
        String currentRsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "";
        String memberCode = config.memberCode();
        if (event.getId() != null && !event.getId().isEmpty()
                && event.getOrganiserRsn() != null
                && event.getOrganiserRsn().equalsIgnoreCase(currentRsn)
                && memberCode != null && !memberCode.isEmpty()) {

            c.gridy = row;
            c.insets = new java.awt.Insets(UIConstants.PADDING_SMALL, 0, 0, 0);
            c.gridwidth = 3;
            c.fill = GridBagConstraints.HORIZONTAL;

            JPanel btnRow = new JPanel(new GridBagLayout());
            btnRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            GridBagConstraints bc = new GridBagConstraints();
            bc.fill = GridBagConstraints.HORIZONTAL;
            bc.weighty = 0;
            bc.gridy = 0;

            JButton editBtn = new JButton("Edit");
            editBtn.setForeground(new Color(0x2196F3));
            bc.gridx = 0; bc.weightx = 1.0;
            bc.insets = new java.awt.Insets(0, 0, 0, 2);
            btnRow.add(editBtn, bc);

            JButton deleteBtn = new JButton("Delete");
            deleteBtn.setForeground(new Color(0xF44336));
            bc.gridx = 1; bc.weightx = 1.0;
            bc.insets = new java.awt.Insets(0, 2, 0, 0);
            btnRow.add(deleteBtn, bc);

            editBtn.addActionListener(e -> showEditDialog(event, memberCode));

            deleteBtn.addActionListener(e -> {
                deleteBtn.setEnabled(false);
                deleteBtn.setText("...");
                contentService.deleteEvent(memberCode, event.getId(),
                    () -> SwingUtilities.invokeLater(this::forceRefresh),
                    err -> SwingUtilities.invokeLater(() -> {
                        deleteBtn.setEnabled(true);
                        deleteBtn.setText("Delete");
                        log.warn("Failed to delete event: {}", err.getMessage());
                    })
                );
            });

            card.add(btnRow, c);
        }

        return card;
    }

    private void showEditDialog(EventDetails event, String memberCode) {
        // Build form fields pre-populated from the event
        JTextField fName = new JTextField(event.getName() != null ? event.getName() : "");
        JTextField fDesc = new JTextField(event.getDescription() != null ? event.getDescription() : "");
        JComboBox<String> fType = new JComboBox<>(new String[]{"PVM", "PVP", "RAID", "PARTY", "MINI", "LEVEL", "SKILL"});
        if (event.getEventType() != null) fType.setSelectedItem(event.getEventType().toUpperCase());
        JTextField fLocation = new JTextField(event.getLocation() != null ? event.getLocation() : "");
        JTextField fWorld = new JTextField(event.getWorld() > 0 ? String.valueOf(event.getWorld()) : "");
        JTextField fPassword = new JTextField(event.getEventPassword() != null ? event.getEventPassword() : "");
        JTextField fChallengePw = new JTextField(event.getChallengePassword() != null ? event.getChallengePassword() : "");
        JTextField fStart = new JTextField(event.getStartTime() != null
            ? INPUT_FMT.format(Instant.parse(event.getStartTime()).atZone(SYDNEY).toLocalDateTime()) : "");
        JTextField fEnd = new JTextField(event.getEndTime() != null
            ? INPUT_FMT.format(Instant.parse(event.getEndTime()).atZone(SYDNEY).toLocalDateTime()) : "");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints lc = new GridBagConstraints();
        lc.fill = GridBagConstraints.HORIZONTAL;
        lc.insets = new java.awt.Insets(2, 4, 2, 4);
        String[] labels = {"Name", "Description", "Type", "Location", "World",
                           "Password", "Challenge PW", "Start (dd/MM/yyyy HH:mm)", "End (dd/MM/yyyy HH:mm)"};
        java.awt.Component[] fields = {fName, fDesc, fType, fLocation, fWorld, fPassword, fChallengePw, fStart, fEnd};
        for (int i = 0; i < labels.length; i++) {
            lc.gridx = 0; lc.gridy = i; lc.weightx = 0;
            form.add(new JLabel(labels[i]), lc);
            lc.gridx = 1; lc.weightx = 1.0;
            form.add(fields[i], lc);
        }

        int result = javax.swing.JOptionPane.showConfirmDialog(
            this, form, "Edit Event",
            javax.swing.JOptionPane.OK_CANCEL_OPTION,
            javax.swing.JOptionPane.PLAIN_MESSAGE
        );
        if (result != javax.swing.JOptionPane.OK_OPTION) return;

        // Validate
        String name = fName.getText().trim();
        String worldStr = fWorld.getText().trim();
        String startStr = fStart.getText().trim();
        String endStr = fEnd.getText().trim();

        if (name.isEmpty() || worldStr.isEmpty() || startStr.isEmpty() || endStr.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(this, "Name, World, Start and End are required.");
            return;
        }

        EventTimeValidation v = validateWorldAndDates(worldStr, startStr, endStr,
            msg -> javax.swing.JOptionPane.showMessageDialog(this, msg));
        if (v == null) return;

        // Build patch body — passwords sent as null if cleared
        JsonObject body = new JsonObject();
        body.addProperty("eventName", name);
        body.addProperty("eventType", (String) fType.getSelectedItem());
        String desc = fDesc.getText().trim();
        if (!desc.isEmpty()) body.addProperty("description", desc);
        body.addProperty("location", fLocation.getText().trim());
        body.addProperty("world", v.world);
        body.addProperty("organiserRsn", event.getOrganiserRsn());
        body.addProperty("startTime", v.startUtc.toString());
        body.addProperty("endTime", v.endUtc.toString());
        body.addProperty("isActive", true);
        String pw = fPassword.getText().trim();
        body.addProperty("eventPassword", pw.isEmpty() ? null : pw);
        String cpw = fChallengePw.getText().trim();
        body.addProperty("challengePassword", cpw.isEmpty() ? null : cpw);

        contentService.patchEvent(memberCode, event.getId(), gson.toJson(body),
            () -> SwingUtilities.invokeLater(this::forceRefresh),
            err -> SwingUtilities.invokeLater(() ->
                javax.swing.JOptionPane.showMessageDialog(this, "Failed: " + err.getMessage()))
        );
    }

    private JLabel makeEventLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        return label;
    }
}
