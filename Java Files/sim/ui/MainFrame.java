package sim.ui;

import sim.model.Flight;
import sim.service.SimulationEngine;
import sim.ui.TicketCounterConfig;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class MainFrame extends JFrame {
    private GlobalInputPanel   globalInputPanel;
    private FlightTablePanel   flightTablePanel;
    private TicketCounterPanel ticketCounterPanel;
    private JButton            startSimulationButton;

    public MainFrame() {
        super("Airport Ticket Counter Setup");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        initializeComponents();
        pack();
        setLocationRelativeTo(null);
    }

    private void initializeComponents() {
        globalInputPanel   = new GlobalInputPanel();
        flightTablePanel   = new FlightTablePanel();
        ticketCounterPanel = new TicketCounterPanel(flightTablePanel.getFlights());
        startSimulationButton = new JButton("Start Simulation");
        startSimulationButton.addActionListener(e -> onStartSimulation());

        // North: global settings
        add(globalInputPanel, BorderLayout.NORTH);

        // Center: tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Flights", flightTablePanel);
        tabs.addTab("Ticket Counters", ticketCounterPanel);
        add(tabs, BorderLayout.CENTER);

        // South: launch button
        add(startSimulationButton, BorderLayout.SOUTH);
    }

    private void onStartSimulation() {
        if (flightTablePanel.getFlights().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please add at least one flight before starting simulation.",
                "No Flights Defined",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<TicketCounterConfig> counters = ticketCounterPanel.getCounters();
        if (counters.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please add at least one ticket counter before starting simulation.",
                "No Counters Defined",
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            double percentInPerson   = globalInputPanel.getPercentInPerson();
            if (percentInPerson < 0 || percentInPerson > 1)
                throw new IllegalArgumentException("Percent in person must be between 0 and 1");

            int    numCheckpoints    = globalInputPanel.getNumCheckpoints();
            double ratePerCheckpoint = globalInputPanel.getRatePerCheckpoint();
            int    arrivalSpan       = globalInputPanel.getArrivalSpanMinutes();
            int    interval          = globalInputPanel.getIntervalMinutes();
            int    transitDelay      = globalInputPanel.getTransitDelayMinutes();
            int    holdDelay         = globalInputPanel.getHoldroomDelayMinutes(); // new
            List<Flight> flights     = flightTablePanel.getFlights();

            // build the pre-run engine for the data table (populate its history)
            SimulationEngine tableEngine = new SimulationEngine(
                percentInPerson,
                counters,
                numCheckpoints,
                ratePerCheckpoint,
                arrivalSpan,
                interval,
                transitDelay,
                holdDelay,      // pass hold-room delay here
                flights
            );
            // ◀– run all intervals to fill history before showing table
            tableEngine.runAllIntervals();

            // build the fresh engine for live animation
            SimulationEngine simEngine = new SimulationEngine(
                percentInPerson,
                counters,
                numCheckpoints,
                ratePerCheckpoint,
                arrivalSpan,
                interval,
                transitDelay,
                holdDelay,      // and here
                flights
            );

            new DataTableFrame(tableEngine).setVisible(true);
            new SimulationFrame(simEngine).setVisible(true);

        } catch (Exception ex) {
            ex.printStackTrace();  // print full stack trace to console
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            JTextArea area = new JTextArea(sw.toString(), 20, 60);
            area.setEditable(false);
            JOptionPane.showMessageDialog(this,
                new JScrollPane(area),
                "Simulation Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
}

