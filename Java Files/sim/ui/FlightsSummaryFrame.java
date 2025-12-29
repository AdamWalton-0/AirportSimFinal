package sim.ui;

import sim.model.Flight;
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
        setLayout(new BorderLayout(10,10));

        List<Flight> flights = engine.getFlights();

        // Recompute the global simulation start time
        LocalTime firstDep   = flights.stream()
            .map(Flight::getDepartureTime)
            .min(LocalTime::compareTo)
            .orElse(LocalTime.MIDNIGHT);
        LocalTime globalStart = firstDep.minusMinutes(engine.getArrivalSpan());

        int cols = Math.min(4, flights.size());  // up to 4 per row
        JPanel grid = new JPanel(new GridLayout(0, cols, 10, 10));

        for (Flight f : flights) {
            // boarding-close time = departure - 20
            String label = f.getFlightNumber() + " @ " +
                           f.getDepartureTime()
                            .minusMinutes(20)
                            .format(TIME_FMT);
            JButton btn = new JButton(label);

            btn.addActionListener(e -> {
                // compute minutes from globalStart to boarding-close
                long minsToClose = Duration.between(
                    globalStart,
                    f.getDepartureTime().minusMinutes(20)
                ).toMinutes();

                // convert minutes to interval index
                int step = (int)(minsToClose / engine.getInterval());

                // show the snapshot at the boarding-close interval
                new FlightSnapshotFrame(engine, f, step)
                    .setVisible(true);
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
}
