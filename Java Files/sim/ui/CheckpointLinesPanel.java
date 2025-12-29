package sim.ui;

import sim.model.Flight;
import sim.model.Passenger;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel displaying checkpoint lines with scrollable grids.
 */
public class CheckpointLinesPanel extends JPanel {
    private final SimulationEngine engine;
    private final int[] checkpointQueuedOffsets;
    private final int[] checkpointServedOffsets;
    private final List<Rectangle> clickableAreas;
    private final List<Passenger> clickablePassengers;
    private final List<Rectangle> counterAreas;
    private final Flight filterFlight;

    public CheckpointLinesPanel(SimulationEngine engine,
                                List<Rectangle> clickableAreas,
                                List<Passenger> clickablePassengers,
                                Flight filterFlight) {
        this.engine = engine;
        this.clickableAreas = clickableAreas;
        this.clickablePassengers = clickablePassengers;
        this.counterAreas = new ArrayList<>();            // << new list for counters
        this.filterFlight = filterFlight;
        this.checkpointQueuedOffsets = new int[engine.getCheckpointLines().size()];
        this.checkpointServedOffsets = new int[engine.getCheckpointLines().size()];
        setFocusable(true);

        // install shared scroll handler
        ScrollMouseHandler handler = new ScrollMouseHandler.CheckpointScrollHandler(
            engine, clickableAreas, clickablePassengers,
            checkpointQueuedOffsets, checkpointServedOffsets,
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
        GridRenderer.renderCheckpointLines(
            this,
            g,
            engine,
            checkpointQueuedOffsets,
            checkpointServedOffsets,
            clickableAreas,
            clickablePassengers,
            counterAreas,
            filterFlight
        );
    }

    /**
     * @return the maximum size that checkpoint line #lineIdx ever reached
     *         across all history intervals.
     */
    public int getMaxQueuedForLine(int lineIdx) {
        int max = 0;
        for (List<List<Passenger>> interval : engine.getHistoryQueuedCheckpoint()) {
            int sz = interval.get(lineIdx).size();
            if (sz > max) {
                max = sz;
            }
        }
        return max;
    }

    /**
     * Ensure a minimum vertical spacing per checkpoint line; enables vertical scrolling
     * when there are many lines.
     */
    @Override
    public Dimension getPreferredSize() {
        int width = super.getPreferredSize().width;
        int lines = engine.getCheckpointLines().size();
        int height = 50 + lines * GridRenderer.MIN_LINE_SPACING + 50;
        return new Dimension(width, height);
    }
}
