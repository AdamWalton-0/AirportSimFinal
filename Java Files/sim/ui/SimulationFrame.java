package sim.ui;

import sim.model.Flight;
import sim.service.SimulationEngine;
import sim.ui.GraphWindow;
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
    private final javax.swing.Timer autoRunTimer;
    private       boolean           isPaused    = false;

    // track, for each flight, the interval index at which it closed
    private final Map<Flight,Integer> closeSteps = new LinkedHashMap<>();

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

        // --- ACTION: Summary button ---
        summaryBtn.addActionListener(e -> 
            // open one clickable summary window
            new FlightsSummaryFrame(engine).setVisible(true)
        );

        // --- TIMER: advance each interval ---
        autoRunTimer = new javax.swing.Timer(speedSlider.getValue(), ev -> {
            javax.swing.Timer t = (javax.swing.Timer)ev.getSource();
            if (engine.getCurrentInterval() < engine.getTotalIntervals()) {
                engine.computeNextInterval();
                LocalTime now = startTime.plusMinutes(engine.getCurrentInterval());
                timeLabel.setText(now.format(TIME_FMT));
                split.repaint();

                // detect newly closed flights
                List<Flight> closed = engine.getFlightsJustClosed();
                if (!closed.isEmpty()) {
                    int step = engine.getCurrentInterval() - 1;
                    for (Flight f : closed) {
                        closeSteps.put(f, step);
                    }
                    t.stop();
                    pausePlayBtn.setText("Play");
                    isPaused = true;
                    for (Flight f : closed) {
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
                    return;
                }

                // end of simulation
                if (engine.getCurrentInterval() >= engine.getTotalIntervals()) {
                    t.stop();
                    autoRunBtn.setEnabled(false);
                    pausePlayBtn.setEnabled(false);
                    summaryBtn.setEnabled(true);
                }
            }
        });

        speedSlider.addChangeListener((ChangeEvent e) ->
            autoRunTimer.setDelay(speedSlider.getValue())
        );

        // --- “Next Interval” button ---
        nextBtn.addActionListener(ev -> {
            engine.computeNextInterval();
            LocalTime now = startTime.plusMinutes(engine.getCurrentInterval());
            timeLabel.setText(now.format(TIME_FMT));
            split.repaint();

            List<Flight> closed = engine.getFlightsJustClosed();
            if (!closed.isEmpty()) {
                int step = engine.getCurrentInterval() - 1;
                for (Flight f : closed) {
                    closeSteps.put(f, step);
                }
            }

            if (engine.getCurrentInterval() >= engine.getTotalIntervals()) {
                nextBtn.setEnabled(false);
                autoRunBtn.setEnabled(false);
                summaryBtn.setEnabled(true);
            }
        });

        autoRunBtn.addActionListener(e -> {
            autoRunBtn.setEnabled(false);
            pausePlayBtn.setVisible(true);
            autoRunTimer.start();
        });
pausePlayBtn.addActionListener(e -> {
    if (isPaused) {
        autoRunTimer.start();
        pausePlayBtn.setText("Pause");
    } else {
        autoRunTimer.stop();
        pausePlayBtn.setText("Play");
    }
    isPaused = !isPaused;
});

        setSize(800, 750);
        setLocationRelativeTo(null);
    }
}
