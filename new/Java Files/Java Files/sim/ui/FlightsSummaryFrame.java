package sim.ui;

import sim.model.Flight;
import sim.model.Passenger;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FlightsSummaryFrame extends JFrame {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public FlightsSummaryFrame(SimulationEngine engine) {
        super("All Flights Summary");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        List<Flight> flights = engine.getFlights();

        // Recompute the global simulation start time (same logic as other UIs)
        LocalTime firstDep = flights.stream()
            .map(Flight::getDepartureTime)
            .min(LocalTime::compareTo)
            .orElse(LocalTime.MIDNIGHT);
        LocalTime globalStart = firstDep.minusMinutes(engine.getArrivalSpan());

        int cols = Math.min(4, flights.size()); // up to 4 per row
        JPanel grid = new JPanel(new GridLayout(0, cols, 10, 10));

        // Determine the latest history step we can safely display (clamp)
        int maxHistoryStep = getMaxHistoryStep(engine);

        for (Flight f : flights) {
            // Boarding-close time = departure - 20
            LocalTime closeTime = f.getDepartureTime().minusMinutes(20);

            // In SimulationEngine, "minute" is currentInterval (1 tick = 1 minute),
            // and closeIdx is computed as minutes between globalStart and (dep-20).
            int closeStep = (int) Duration.between(globalStart, closeTime).toMinutes();

            // Clamp step to history bounds (protects against partial runs)
            int step = Math.max(0, Math.min(closeStep, maxHistoryStep));

            // Optional: show how many made it by the close moment (from historyHoldRooms)
            String madeText = "";
            try {
                int total = (int) Math.round(f.getSeats() * f.getFillPercent());
                int flightIdx = flights.indexOf(f);

                int made = 0;
                if (engine.getHistoryHoldRooms() != null
                    && step < engine.getHistoryHoldRooms().size()
                    && flightIdx >= 0
                    && step >= 0) {

                    List<List<Passenger>> holdAtStep = engine.getHistoryHoldRooms().get(step);
                    if (flightIdx < holdAtStep.size()) {
                        made = holdAtStep.get(flightIdx).size();
                    }
                } else {
                    // fallback (should rarely be needed)
                    if (flightIdx >= 0 && flightIdx < engine.getHoldRoomLines().size()) {
                        made = engine.getHoldRoomLines().get(flightIdx).size();
                    }
                }
                madeText = String.format("  (%d/%d)", made, total);
            } catch (Exception ignored) {
                // If anything about history indexing changes, keep the button working regardless.
            }

            String label = f.getFlightNumber() + " @ " + closeTime.format(TIME_FMT) + madeText;
            JButton btn = new JButton(label);

            // Helpful tooltip: exact step used (and whether clamped)
            String tip = "Snapshot step: " + step;
            if (step != closeStep) {
                tip += " (clamped from " + closeStep + ")";
            }
            btn.setToolTipText(tip);

            btn.addActionListener(e -> {
                // Show the snapshot at the boarding-close history step.
                // FlightSnapshotFrame will also clamp internally as an extra safeguard.
                new FlightSnapshotFrame(engine, f, step).setVisible(true);
            });

            grid.add(btn);
        }

        JScrollPane scroll = new JScrollPane(
            grid,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        add(scroll, BorderLayout.CENTER);

        pack();
        setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        setVisible(true);
    }

    /**
     * Compute the safest maximum step that exists across the key history lists.
     * This keeps snapshots from trying to index beyond recorded history.
     */
    private int getMaxHistoryStep(SimulationEngine engine) {
        try {
            int a = engine.getHistoryQueuedTicket() != null ? engine.getHistoryQueuedTicket().size() : 0;
            int b = engine.getHistoryQueuedCheckpoint() != null ? engine.getHistoryQueuedCheckpoint().size() : 0;
            int c = engine.getHistoryHoldRooms() != null ? engine.getHistoryHoldRooms().size() : 0;

            int min = Math.min(a, Math.min(b, c));
            return Math.max(0, min - 1);
        } catch (Exception ex) {
            return 0;
        }
    }
}
