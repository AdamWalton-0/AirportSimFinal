package sim.ui;

import sim.model.Flight;
import sim.service.SimulationEngine;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SimulationFrame extends JFrame {
    private final JLabel            timeLabel;
    private final LocalTime         startTime;
    private final DateTimeFormatter TIME_FMT     = DateTimeFormatter.ofPattern("HH:mm");

    private final JButton           autoRunBtn;
    private final JButton           pausePlayBtn;
    private final JButton           summaryBtn;
    private final JSlider           speedSlider;

    // remove final so it can be referenced in lambdas before assignment without "definite assignment" errors
    private javax.swing.Timer       autoRunTimer;

    private       boolean           isPaused    = false;

    // Rewind + scrub controls
    private final JButton           prevBtn;
    private final JSlider           timelineSlider;
    private final JLabel            intervalLabel;

    // Guard: prevents programmatic timelineSlider.setValue() from triggering scrub logic
    private boolean                 timelineProgrammaticUpdate = false;

    // Arrivals graph tab (strongly typed so we can call syncWithEngine/setViewedInterval)
    private final ArrivalsGraphPanel arrivalsGraphPanel;

    // track, for each flight, the interval index at which it closed
    private final Map<Flight,Integer> closeSteps = new LinkedHashMap<>();

    // track whether we have finished at least once (enables Summary permanently)
    private boolean simulationCompleted = false;

    public SimulationFrame(SimulationEngine engine) {
        super("Simulation View");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        // compute start time
        LocalTime firstDep = engine.getFlights().stream()
            .map(Flight::getDepartureTime)
            .min(LocalTime::compareTo)
            .orElse(LocalTime.MIDNIGHT);
        startTime = firstDep.minusMinutes(engine.getArrivalSpan());

        // === Top panel with BoxLayout for precise width control ===
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.X_AXIS));

        // --- Legend panel (left) ---
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        legendPanel.setBorder(BorderFactory.createTitledBorder("Legend"));
        for (Flight f : engine.getFlights()) {
            legendPanel.add(new JLabel(f.getShape().name() + " = " + f.getFlightNumber()));
        }
        topPanel.add(legendPanel);

        // --- Spacer to push time box toward center ---
        topPanel.add(Box.createHorizontalGlue());

        // --- Time label and container ---
        timeLabel = new JLabel(startTime.format(TIME_FMT));
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 16f));
        timeLabel.setBorder(BorderFactory.createTitledBorder("Current Time"));
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Fixed-size wrapper for time box
        JPanel timePanel = new JPanel();
        timePanel.setLayout(new BorderLayout());
        timePanel.setPreferredSize(new Dimension(180, 50));
        timePanel.add(timeLabel, BorderLayout.CENTER);

        timePanel.setPreferredSize(new Dimension(180, 50));
        timePanel.setMaximumSize(new Dimension(180, 50));  // cap size

        topPanel.add(timePanel);
        topPanel.add(Box.createRigidArea(new Dimension(20, 0))); // right padding

        // Attach to frame
        add(topPanel, BorderLayout.NORTH);

        // --- CENTER: live panels in a scrollable strip ---
        JPanel split = new JPanel();
        split.setLayout(new BoxLayout(split, BoxLayout.X_AXIS));
        int cellW   = 60 / 3, boxSize = 60, gutter = 30, padding = 100;
        int queuedW = GridRenderer.COLS * cellW,
            servedW = GridRenderer.COLS * cellW,
            panelW  = queuedW + boxSize + servedW + padding;

        // Ticket panel
        TicketLinesPanel ticketPanel = new TicketLinesPanel(
            engine, new ArrayList<>(), new ArrayList<>(), null
        );
        Dimension tPref = ticketPanel.getPreferredSize();
        ticketPanel.setPreferredSize(new Dimension(panelW, tPref.height));
        ticketPanel.setMinimumSize(ticketPanel.getPreferredSize());
        ticketPanel.setMaximumSize(ticketPanel.getPreferredSize());
        split.add(Box.createHorizontalStrut(gutter));
        split.add(ticketPanel);

        // Checkpoint panel
        split.add(Box.createHorizontalStrut(gutter));
        CheckpointLinesPanel cpPanel = new CheckpointLinesPanel(
            engine, new ArrayList<>(), new ArrayList<>(), null
        );
        Dimension cPref = cpPanel.getPreferredSize();
        cpPanel.setPreferredSize(new Dimension(panelW, cPref.height));
        cpPanel.setMinimumSize(cpPanel.getPreferredSize());
        cpPanel.setMaximumSize(cpPanel.getPreferredSize());
        split.add(cpPanel);

        // Hold-rooms panel
        split.add(Box.createHorizontalStrut(gutter));
        HoldRoomsPanel holdPanel = new HoldRoomsPanel(
            engine, new ArrayList<>(), new ArrayList<>(), null
        );
        split.add(holdPanel);

        JScrollPane centerScroll = new JScrollPane(
            split,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS
        );
        add(centerScroll, BorderLayout.CENTER);

        // --- SOUTH: controls & speed slider ---
        JPanel control = new JPanel();
        control.setLayout(new BoxLayout(control, BoxLayout.Y_AXIS));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Prev Interval button
        prevBtn = new JButton("Prev Interval");
        btnPanel.add(prevBtn);

        JButton nextBtn = new JButton("Next Interval");
        btnPanel.add(nextBtn);

        autoRunBtn   = new JButton("AutoRun");
        pausePlayBtn = new JButton("Pause");
        summaryBtn   = new JButton("Summary");

        summaryBtn.setEnabled(false);
        pausePlayBtn.setVisible(false);

        btnPanel.add(autoRunBtn);
        btnPanel.add(pausePlayBtn);

        JButton graphBtn = new JButton("Show Graph");
        graphBtn.addActionListener(e -> {
            Map<Integer, Integer> heldUps = engine.getHoldUpsByInterval();
            new GraphWindow("Passenger Hold-Ups by Interval", heldUps).setVisible(true);
        });
        btnPanel.add(graphBtn);

        btnPanel.add(summaryBtn);
        control.add(btnPanel);

        // === Tabs: Timeline + Arrivals Graph (same area as timeline) ===
        JPanel timelineAndGraphContainer = new JPanel(new BorderLayout(8, 6));
        timelineAndGraphContainer.setBorder(
            BorderFactory.createTitledBorder("Timeline (rewind / review computed intervals)")
        );

        JTabbedPane tabs = new JTabbedPane();

        // ----- Timeline tab -----
        JPanel timelineTab = new JPanel(new BorderLayout(8, 4));

        // FIX: put the intervalLabel ABOVE the slider so it can never collide with slider labels
        intervalLabel = new JLabel();
        intervalLabel.setPreferredSize(new Dimension(260, 20));
        intervalLabel.setHorizontalAlignment(SwingConstants.LEFT);

        timelineSlider = new JSlider(0, Math.max(0, engine.getMaxComputedInterval()), 0);
        timelineSlider.setPaintTicks(true);
        timelineSlider.setPaintLabels(true);
        timelineSlider.setMajorTickSpacing(10);
        timelineSlider.setMinorTickSpacing(1);

        // Initial label table (will be rebuilt dynamically in refreshUI)
        rebuildTimelineLabels(timelineSlider);

        timelineTab.add(intervalLabel, BorderLayout.NORTH);
        timelineTab.add(timelineSlider, BorderLayout.CENTER);

        tabs.addTab("Timeline", timelineTab);

        // ----- Arrivals Graph tab -----
        arrivalsGraphPanel = new ArrivalsGraphPanel(engine);
        JPanel arrivalsTab = new JPanel(new BorderLayout());
        arrivalsTab.add(arrivalsGraphPanel, BorderLayout.CENTER);
        tabs.addTab("Arrivals", arrivalsTab);

        timelineAndGraphContainer.add(tabs, BorderLayout.CENTER);
        control.add(timelineAndGraphContainer);

        JPanel sliderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sliderPanel.setBorder(BorderFactory.createTitledBorder(
            "AutoRun Speed (ms per interval)"
        ));
        speedSlider = new JSlider(100, 2000, 1000);
        speedSlider.setMajorTickSpacing(500);
        speedSlider.setMinorTickSpacing(100);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        Hashtable<Integer,JLabel> labels = new Hashtable<>();
        labels.put(100,  new JLabel("0.1s"));
        labels.put(500,  new JLabel("0.5s"));
        labels.put(1000, new JLabel("1s"));
        labels.put(1500, new JLabel("1.5s"));
        labels.put(2000, new JLabel("2s"));
        speedSlider.setLabelTable(labels);
        sliderPanel.add(speedSlider);
        control.add(sliderPanel);

        add(control, BorderLayout.SOUTH);

        // Summary button
        summaryBtn.addActionListener(e ->
            new FlightsSummaryFrame(engine).setVisible(true)
        );

        // Helper: refresh UI from engine state
        Runnable refreshUI = () -> {
            LocalTime now = startTime.plusMinutes(engine.getCurrentInterval());
            timeLabel.setText(now.format(TIME_FMT));
            split.repaint();

            // keep timeline slider bounded to what exists
            int maxComputed = engine.getMaxComputedInterval();

            // IMPORTANT: prevent programmatic slider updates from triggering scrub listener
            timelineProgrammaticUpdate = true;
            try {
                if (timelineSlider.getMaximum() != maxComputed) {
                    timelineSlider.setMaximum(maxComputed);

                    // dynamic tick spacing + label table to avoid overlap
                    int major = computeMajorTickSpacing(maxComputed);
                    timelineSlider.setMajorTickSpacing(major);
                    timelineSlider.setMinorTickSpacing(1);

                    rebuildTimelineLabels(timelineSlider);
                }

                // set slider value to current interval if within bounds
                int ci = engine.getCurrentInterval();
                if (ci <= timelineSlider.getMaximum()) {
                    timelineSlider.setValue(ci);
                } else {
                    timelineSlider.setValue(timelineSlider.getMaximum());
                }
            } finally {
                timelineProgrammaticUpdate = false;
            }

            intervalLabel.setText("Interval: " + engine.getCurrentInterval()
                    + " / " + engine.getTotalIntervals());

            // update arrivals graph: extend series + move marker
            arrivalsGraphPanel.syncWithEngine();

            // enable/disable controls
            prevBtn.setEnabled(engine.canRewind());

            boolean canAdvance = engine.getCurrentInterval() < engine.getTotalIntervals();
            nextBtn.setEnabled(canAdvance);

            // AutoRun button is only enabled when not currently running
            if (autoRunTimer == null || !autoRunTimer.isRunning()) {
                autoRunBtn.setEnabled(canAdvance);
            }

            // Summary stays enabled once simulation has completed at least once
            if (simulationCompleted) {
                summaryBtn.setEnabled(true);
            }
        };

        // Helper: handle closures without repeating dialogs when revisiting via rewind
        java.util.function.Consumer<List<Flight>> handleClosures = (closed) -> {
            if (closed == null || closed.isEmpty()) return;

            int step = engine.getCurrentInterval() - 1;

            // only treat as "new" closure if we haven't recorded it before
            List<Flight> newlyClosed = new ArrayList<>();
            for (Flight f : closed) {
                if (!closeSteps.containsKey(f)) {
                    closeSteps.put(f, step);
                    newlyClosed.add(f);
                }
            }

            // If we are rewinding/replaying, do not stop or re-alert unless truly new
            if (newlyClosed.isEmpty()) return;

            if (autoRunTimer != null && autoRunTimer.isRunning()) {
                autoRunTimer.stop();
                pausePlayBtn.setText("Play");
                isPaused = true;
            }

            for (Flight f : newlyClosed) {
                int total = (int)Math.round(f.getSeats() * f.getFillPercent());
                int idx   = engine.getFlights().indexOf(f);
                int made  = engine.getHoldRoomLines().get(idx).size();
                JOptionPane.showMessageDialog(
                        SimulationFrame.this,
                        String.format("%s: %d of %d made their flight.",
                                f.getFlightNumber(), made, total),
                        "Flight Closed",
                        JOptionPane.INFORMATION_MESSAGE
                );
            }
        };

        // --- TIMER: advance each interval ---
        autoRunTimer = new javax.swing.Timer(speedSlider.getValue(), ev -> {
            javax.swing.Timer t = (javax.swing.Timer)ev.getSource();
            if (engine.getCurrentInterval() < engine.getTotalIntervals()) {
                engine.computeNextInterval();
                refreshUI.run();

                // detect newly closed flights
                List<Flight> closed = engine.getFlightsJustClosed();
                handleClosures.accept(closed);

                // end of simulation
                if (engine.getCurrentInterval() >= engine.getTotalIntervals()) {
                    simulationCompleted = true;
                    t.stop();
                    autoRunBtn.setEnabled(false);
                    pausePlayBtn.setEnabled(false);
                    summaryBtn.setEnabled(true);
                }
            }
        });

        speedSlider.addChangeListener((ChangeEvent e) -> {
            if (autoRunTimer != null) {
                autoRunTimer.setDelay(speedSlider.getValue());
            }
        });

        // --- Prev Interval ---
        prevBtn.addActionListener(ev -> {
            if (autoRunTimer != null && autoRunTimer.isRunning()) {
                autoRunTimer.stop();
                pausePlayBtn.setText("Play");
                isPaused = true;
            }
            engine.rewindOneInterval();
            refreshUI.run();
        });

        // --- Next Interval ---
        nextBtn.addActionListener(ev -> {
            engine.computeNextInterval();
            refreshUI.run();

            List<Flight> closed = engine.getFlightsJustClosed();
            handleClosures.accept(closed);

            if (engine.getCurrentInterval() >= engine.getTotalIntervals()) {
                simulationCompleted = true;
                nextBtn.setEnabled(false);
                autoRunBtn.setEnabled(false);
                summaryBtn.setEnabled(true);
            }
        });

        // Timeline scrub (jump to computed interval)
        timelineSlider.addChangeListener((ChangeEvent e) -> {
            // ignore programmatic updates coming from refreshUI()
            if (timelineProgrammaticUpdate) return;

            // only commit jump when user releases the slider thumb
            if (timelineSlider.getValueIsAdjusting()) {
                intervalLabel.setText("Interval: " + timelineSlider.getValue()
                        + " / " + engine.getTotalIntervals());

                // move marker while dragging
                arrivalsGraphPanel.setViewedInterval(timelineSlider.getValue());
                return;
            }

            int target = timelineSlider.getValue();

            // stop autorun before jump
            if (autoRunTimer != null && autoRunTimer.isRunning()) {
                autoRunTimer.stop();
                pausePlayBtn.setText("Play");
                isPaused = true;
            }

            engine.goToInterval(target);
            refreshUI.run();
        });

        autoRunBtn.addActionListener(e -> {
            autoRunBtn.setEnabled(false);
            pausePlayBtn.setVisible(true);

            pausePlayBtn.setText("Pause");
            isPaused = false;

            if (autoRunTimer != null) {
                autoRunTimer.start();
            }
        });

        pausePlayBtn.addActionListener(e -> {
            if (autoRunTimer == null) return;

            if (isPaused) {
                autoRunTimer.start();
                pausePlayBtn.setText("Pause");
            } else {
                autoRunTimer.stop();
                pausePlayBtn.setText("Play");
            }
            isPaused = !isPaused;
            refreshUI.run();
        });

        // Initial UI sync
        refreshUI.run();

        setSize(800, 820);
        setLocationRelativeTo(null);
    }

    /**
     * Choose a major tick spacing that prevents label overlap for large interval counts.
     *
     * Updated rules requested:
     *  - 100–150   -> 20s
     *  - 150–500   -> 50s
     *  - 500–1000  -> 100s
     *  - 1000+     -> 500s
     *
     * For <100, keep a reasonable default.
     */
    private static int computeMajorTickSpacing(int maxIntervals) {
        if (maxIntervals >= 1000) return 500;
        if (maxIntervals >= 500)  return 100;
        if (maxIntervals >= 150)  return 50;   // 150..500 uses 50s
        if (maxIntervals >= 100)  return 20;   // 100..149 uses 20s

        // < 100: keep readable labels without clutter
        if (maxIntervals >= 50) return 10;
        if (maxIntervals >= 20) return 5;
        return 1;
    }

    /**
     * Rebuild label table so Swing doesn't try to paint too many labels.
     * Also prevents the far-right "max" label from overlapping the last major label.
     */
    private static void rebuildTimelineLabels(JSlider slider) {
        int max = slider.getMaximum();
        int major = slider.getMajorTickSpacing();
        if (major <= 0) major = 1;

        Hashtable<Integer, JLabel> table = new Hashtable<>();

        // Always label 0
        table.put(0, new JLabel("0"));

        // Label major ticks
        for (int v = major; v < max; v += major) {
            table.put(v, new JLabel(String.valueOf(v)));
        }

        if (max != 0) {
            // Avoid overlap at the far right:
            // If max is very close to the last major tick, replace that last major label with max.
            int lastMajor = (max / major) * major;
            if (lastMajor == max) {
                table.put(max, new JLabel(String.valueOf(max)));
            } else {
                // "Too close" threshold: if the gap is small, lastMajor + max labels collide visually.
                // Replacing keeps a clean far-right label.
                if (lastMajor > 0 && (max - lastMajor) < (major / 2)) {
                    table.remove(lastMajor);
                }
                table.put(max, new JLabel(String.valueOf(max)));
            }
        }

        slider.setLabelTable(table);
        slider.setPaintLabels(true);
        slider.setPaintTicks(true);
        slider.repaint();
    }
}
