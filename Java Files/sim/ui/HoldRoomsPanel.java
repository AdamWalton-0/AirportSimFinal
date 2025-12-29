package sim.ui;

import sim.model.Flight;
import sim.model.Passenger;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;                // <— this brings in Rectangle, Dimension, Graphics, etc.
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class HoldRoomsPanel extends JPanel {
    private static final int HOLD_BOX_SIZE = GridRenderer.HOLD_BOX_SIZE;
    private static final int HOLD_GAP      = GridRenderer.HOLD_GAP;

    private final SimulationEngine engine;
    private final Flight           filterFlight;
    private final List<Rectangle>  clickableAreas;
    private final List<Passenger>  clickablePassengers;

    public HoldRoomsPanel(SimulationEngine engine,
                            List<Rectangle> clickableAreas,
                            List<Passenger> clickablePassengers,
                            Flight filterFlight) {
        this.engine              = engine;
        this.filterFlight        = filterFlight;
        this.clickableAreas      = clickableAreas;
        this.clickablePassengers = clickablePassengers;

        int count  = engine.getFlights().size();
        int width  = HOLD_GAP + count * (HOLD_BOX_SIZE + HOLD_GAP);
        int height = HOLD_BOX_SIZE + 2 * HOLD_GAP;
        setPreferredSize(new Dimension(width, height));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (int i = 0; i < clickableAreas.size(); i++) {
                    if (clickableAreas.get(i).contains(e.getPoint())) {
                        Passenger p = clickablePassengers.get(i);
                        showPassengerDetails(p);
                        return;
                    }
                }
            }
        });
    }

    public HoldRoomsPanel(SimulationEngine engine, Flight filterFlight) {
        this(engine, new ArrayList<>(), new ArrayList<>(), filterFlight);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        GridRenderer.renderHoldRooms(
            this, g, engine,
            clickableAreas, clickablePassengers,
            filterFlight
        );
    }

    private void showPassengerDetails(Passenger p) {
       // compute sim start
        LocalTime simStart = p.getFlight()

            .getDepartureTime()
            .minusMinutes(engine.getArrivalSpan());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder msg = new StringBuilder();
        msg.append("Flight: ").append(p.getFlight().getFlightNumber());
        msg.append("\nArrived at: ")
            .append(simStart.plusMinutes(p.getArrivalMinute()).format(fmt));
        msg.append("\nPurchase Type: ")
            .append(p.isInPerson() ? "In Person" : "Online");

        if (p.isInPerson() && p.getTicketCompletionMinute() >= 0) {
            msg.append("\nTicketed at: ")
                .append(simStart.plusMinutes(p.getTicketCompletionMinute()).format(fmt));
        }
        if (p.getCheckpointEntryMinute() >= 0) {
            msg.append("\nCheckpoint Entry: ")
                .append(simStart.plusMinutes(p.getCheckpointEntryMinute()).format(fmt));
        }
        if (p.getCheckpointCompletionMinute() >= 0) {
            msg.append("\nCheckpoint Completion: ")
                .append(simStart.plusMinutes(p.getCheckpointCompletionMinute()).format(fmt));
        }

       // ← NEW hold-room info
        if (p.getHoldRoomEntryMinute() >= 0) {
            msg.append("\nHold-room Entry: ")
                .append(simStart.plusMinutes(p.getHoldRoomEntryMinute()).format(fmt));
        }
        if (p.getHoldRoomSequence() >= 0) {
            msg.append("\nHold-room Seq #: ")
                .append(p.getHoldRoomSequence());
        }

        msg.append("\nMissed: ").append(p.isMissed());

        JOptionPane.showMessageDialog(
            this,
            msg.toString(),
            "Passenger Details",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
}
