package sim.ui;

import sim.model.Flight;
import sim.model.Passenger;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel displaying ticket lines with scrollable grids.
 */
public class TicketLinesPanel extends JPanel {
    private final SimulationEngine engine;
    private final int[] queuedOffsets;
    private final int[] servedOffsets;
    private final List<Rectangle> clickableAreas;
    private final List<Passenger> clickablePassengers;
    private final List<Rectangle> counterAreas;
    private final Flight filterFlight;

    public TicketLinesPanel(SimulationEngine engine,
                             List<Rectangle> clickableAreas,
                             List<Passenger> clickablePassengers,
                             Flight filterFlight) {
        this.engine = engine;
        this.clickableAreas = clickableAreas;
        this.clickablePassengers = clickablePassengers;
        this.counterAreas = new ArrayList<>();          // << new list for counters
        this.filterFlight = filterFlight;
        this.queuedOffsets = new int[engine.getTicketLines().size()];
        this.servedOffsets = new int[engine.getTicketLines().size()];
        setFocusable(true);

        // install shared scroll handler
        ScrollMouseHandler handler = new ScrollMouseHandler.TicketScrollHandler(
            engine, clickableAreas, clickablePassengers,
            queuedOffsets, servedOffsets,
            filterFlight,
            counterAreas
        );
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // now pass 'this' plus the counterAreas list into the renderer
        GridRenderer.renderTicketLines(
            this,
            g,
            engine,
            queuedOffsets,
            servedOffsets,
            clickableAreas,
            clickablePassengers,
            counterAreas,
            filterFlight
        );
    }

    /**
     * @return the maximum size that line #lineIdx ever reached
     *         across all history intervals.
     */
    public int getMaxQueuedForLine(int lineIdx) {
        int max = 0;
        // iterate through each saved interval of queued-ticket history
        for (List<List<Passenger>> interval : engine.getHistoryQueuedTicket()) {
            int sz = interval.get(lineIdx).size();
            if (sz > max) {
                max = sz;
            }
        }
        return max;
    }

    /**
     * Ensure a minimum vertical spacing per line; enables vertical scrolling
     * when there are many lines.
     */
    @Override
    public Dimension getPreferredSize() {
        // keep current width calculation
        int width = super.getPreferredSize().width;
        // number of ticket lines
        int lines = engine.getTicketLines().size();
        // 50px top + 50px bottom margins
        int height = 50 + lines * GridRenderer.MIN_LINE_SPACING + 50;
        return new Dimension(width, height);
    }
}
