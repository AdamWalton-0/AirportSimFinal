package sim.ui;

import sim.model.Passenger;
import sim.model.Flight.ShapeType;
import sim.service.SimulationEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;

public class SimulationPanel extends JPanel {
    private static final int ROWS = 3;
    private static final int COLS = 15;

    private final SimulationEngine engine;
    private final int[] ticketQueuedOffsets;
    private final int[] ticketServedOffsets;
    private final int[] checkpointQueuedOffsets;
    private final int[] checkpointServedOffsets;

    //–– click-to-inspect support
    private final List<Rectangle> clickableAreas = new ArrayList<>();
    private final List<Passenger> clickablePassengers = new ArrayList<>();

    // Drag state
    private boolean dragging = false;
    private boolean draggingQueued;   // true=queued knob, false=served knob
    private boolean draggingTicket;   // true=ticket line, false=checkpoint line
    private int dragLine = -1;
    private int initialMouseX;
    private int initialOffset;

    public SimulationPanel(SimulationEngine engine) {
        this.engine = engine;
        setFocusable(true);
        this.ticketQueuedOffsets     = new int[engine.getTicketLines().size()];
        this.ticketServedOffsets     = new int[engine.getTicketLines().size()];
        this.checkpointQueuedOffsets = new int[engine.getCheckpointLines().size()];
        this.checkpointServedOffsets = new int[engine.getCheckpointLines().size()];

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { handlePress(e); }
            @Override public void mouseReleased(MouseEvent e) { dragging = false; }
            @Override public void mouseDragged(MouseEvent e)  { handleDrag(e); }
            @Override public void mouseClicked(MouseEvent e)  { handleClick(e); }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    private void handlePress(MouseEvent e) {
        int mx = e.getX(), my = e.getY();
        int w = getWidth(), h = getHeight();
        int leftX = w / 4, rightX = 3 * w / 4;
        int top = 50, bottom = h - 50;
        int boxSize = 60;
        int cellW = boxSize / ROWS;
        int gridWidth = COLS * cellW;
        int gridHeight = ROWS * cellW;
        int trackH = cellW / 2;

        // Ticket queued scrollbar zones
        int lines = engine.getTicketLines().size();
        int space = lines > 1 ? (bottom - top) / (lines - 1) : 0;
        for (int i = 0; i < lines; i++) {
            List<Passenger> q = engine.getHistoryQueuedTicket().get(engine.getCurrentInterval() - 1).get(i);
            int fullCols = (q.size() + ROWS - 1) / ROWS;
            if (fullCols <= COLS) continue;
            int centerY = space > 0 ? top + i * space : h / 2;
            int boxX = leftX - boxSize / 2, boxY = centerY - boxSize / 2;
            int trackX = boxX - gridWidth;
            int trackY = boxY + (boxSize - gridHeight) / 2 + gridHeight + 2;
            int knobW = (int)((COLS / (double)fullCols) * gridWidth);
            int knobX = trackX + (int)(ticketQueuedOffsets[i] / (double)(fullCols - COLS) * (gridWidth - knobW));
            if (mx >= knobX && mx <= knobX + knobW && my >= trackY && my <= trackY + trackH) {
                dragging = true;
                draggingQueued = true;
                draggingTicket = true;
                dragLine = i;
                initialMouseX = mx;
                initialOffset = ticketQueuedOffsets[i];
                return;
            }
        }

        // Ticket served scrollbar zones
        for (int i = 0; i < lines; i++) {
            List<Passenger> s = engine.getHistoryServedTicket().get(engine.getCurrentInterval() - 1).get(i);
            int fullCols = (s.size() + ROWS - 1) / ROWS;
            if (fullCols <= COLS) continue;
            int centerY = space > 0 ? top + i * space : h / 2;
            int boxX = leftX - boxSize / 2, boxY = centerY - boxSize / 2;
            int trackX = boxX + boxSize;
            int trackY = boxY + (boxSize - gridHeight) / 2 + gridHeight + 2;
            int knobW = (int)((COLS / (double)fullCols) * gridWidth);
            int knobX = trackX + (int)(ticketServedOffsets[i] / (double)(fullCols - COLS) * (gridWidth - knobW));
            if (mx >= knobX && mx <= knobX + knobW && my >= trackY && my <= trackY + trackH) {
                dragging = true;
                draggingQueued = false;
                draggingTicket = true;
                dragLine = i;
                initialMouseX = mx;
                initialOffset = ticketServedOffsets[i];
                return;
            }
        }

        // Checkpoint queued scrollbar zones
        lines = engine.getCheckpointLines().size();
        space = lines > 1 ? (bottom - top) / (lines - 1) : 0;
        for (int i = 0; i < lines; i++) {
            // ← now using the live queue
            List<Passenger> q = engine.getCheckpointLines().get(i);
            int fullCols = (q.size() + ROWS - 1) / ROWS;
            if (fullCols <= COLS) continue;
            int centerY = space > 0 ? top + i * space : h / 2;
            int boxX = rightX - boxSize / 2, boxY = centerY - boxSize / 2;
            int trackX = boxX - gridWidth;
            int trackY = boxY + (boxSize - gridHeight) / 2 + gridHeight + 2;
            int knobW = (int)((COLS / (double)fullCols) * gridWidth);
            int knobX = trackX + (int)(checkpointQueuedOffsets[i] / (double)(fullCols - COLS) * (gridWidth - knobW));
            if (mx >= knobX && mx <= knobX + knobW && my >= trackY && my <= trackY + trackH) {
                dragging = true;
                draggingQueued = true;
                draggingTicket = false;
                dragLine = i;
                initialMouseX = mx;
                initialOffset = checkpointQueuedOffsets[i];
                return;
            }
        }

        // Checkpoint served scrollbar zones
        for (int i = 0; i < lines; i++) {
            // ← now using the completed list
            List<Passenger> s = engine.getCompletedCheckpointLines().get(i);
            int fullCols = (s.size() + ROWS - 1) / ROWS;
            if (fullCols <= COLS) continue;
            int centerY = space > 0 ? top + i * space : h / 2;
            int boxX = rightX - boxSize / 2, boxY = centerY - boxSize / 2;
            int trackX = boxX + boxSize;
            int trackY = boxY + (boxSize - gridHeight) / 2 + gridHeight + 2;
            int knobW = (int)((COLS / (double)fullCols) * gridWidth);
            int knobX = trackX + (int)(checkpointServedOffsets[i] / (double)(fullCols - COLS) * (gridWidth - knobW));
            if (mx >= knobX && mx <= knobX + knobW && my >= trackY && my <= trackY + trackH) {
                dragging = true;
                draggingQueued = false;
                draggingTicket = false;
                dragLine = i;
                initialMouseX = mx;
                initialOffset = checkpointServedOffsets[i];
                return;
            }
        }
    }

    private void handleDrag(MouseEvent e) {
        if (!dragging) return;
        int dx = e.getX() - initialMouseX;
        int cellW = 60 / ROWS;

        if (draggingTicket) {
            if (draggingQueued) {
                List<Passenger> q = engine.getHistoryQueuedTicket().get(engine.getCurrentInterval() - 1).get(dragLine);
                int fullCols = (q.size() + ROWS - 1) / ROWS;
                int off = initialOffset + dx / cellW;
                ticketQueuedOffsets[dragLine] = Math.max(0, Math.min(off, fullCols - COLS));
            } else {
                List<Passenger> s = engine.getHistoryServedTicket().get(engine.getCurrentInterval() - 1).get(dragLine);
                int fullCols = (s.size() + ROWS - 1) / ROWS;
                int off = initialOffset + dx / cellW;
                ticketServedOffsets[dragLine] = Math.max(0, Math.min(off, fullCols - COLS));
            }
        } else {
            if (draggingQueued) {
                // ← live queue here too
                List<Passenger> q = engine.getCheckpointLines().get(dragLine);
                int fullCols = (q.size() + ROWS - 1) / ROWS;
                int off = initialOffset + dx / cellW;
                checkpointQueuedOffsets[dragLine] = Math.max(0, Math.min(off, fullCols - COLS));
            } else {
                // ← completed list here too
                List<Passenger> s = engine.getCompletedCheckpointLines().get(dragLine);
                int fullCols = (s.size() + ROWS - 1) / ROWS;
                int off = initialOffset + dx / cellW;
                checkpointServedOffsets[dragLine] = Math.max(0, Math.min(off, fullCols - COLS));
            }
        }

        repaint();
    }

    private void handleClick(MouseEvent e) {
        Point pt = e.getPoint();
        for (int i = 0; i < clickableAreas.size(); i++) {
            if (clickableAreas.get(i).contains(pt)) {
                Passenger p = clickablePassengers.get(i);
                java.time.LocalTime start = p.getFlight()
                                             .getDepartureTime()
                                             .minusMinutes(engine.getArrivalSpan());
                java.time.LocalTime arrival = start.plusMinutes(p.getArrivalMinute());
                String msg = "Flight: " + p.getFlight().getFlightNumber()
                           + "\nArrived: " + arrival.format(
                                 java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                JOptionPane.showMessageDialog(
                  this, msg, "Passenger Info", JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        clickableAreas.clear();
        clickablePassengers.clear();

        int w = getWidth(), h = getHeight();
        int leftX = w / 4, rightX = 3 * w / 4;
        int top = 50, bottom = h - 50;
        int boxSize = 60;
        int cellW = boxSize / ROWS;
        int gridHeight = ROWS * cellW;
        int gridWidth = COLS * cellW;
        int trackH = cellW / 2;

        int step = engine.getCurrentInterval() - 1;
        if (step < 0) return;

        // Ticket lines
        int lines = engine.getTicketLines().size();
        int space = lines > 1 ? (bottom - top) / (lines - 1) : 0;
        for (int i = 0; i < lines; i++) {
            int centerY = space > 0 ? top + i * space : h / 2;
            int boxX = leftX - boxSize / 2, boxY = centerY - boxSize / 2;
            g.setColor(Color.BLACK);
            g.drawRect(boxX, boxY, boxSize, boxSize);

            // queued waiting
            g.setColor(Color.YELLOW);
            List<Passenger> queued = engine.getHistoryQueuedTicket().get(step).get(i);
            int startXq = boxX - cellW;
            drawGridPartial(g, queued,
                            startXq,
                            boxY + (boxSize - gridHeight)/2,
                            cellW, cellW, ROWS,
                            ticketQueuedOffsets[i],
                            true);

            // queued scrollbar
            int fullColsQ = (queued.size() + ROWS - 1) / ROWS;
            if (fullColsQ > COLS) {
                int trackXq = startXq - (COLS - 1) * cellW;
                int trackYq = boxY + (boxSize - gridHeight)/2 + gridHeight + 2;
                int trackWq = COLS * cellW;
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(trackXq, trackYq, trackWq, trackH);
                int knobWq = (int)((COLS/(double)fullColsQ)*trackWq);
                int knobXq = trackXq + (int)(ticketQueuedOffsets[i]/(double)(fullColsQ-COLS)*(trackWq-knobWq));
                g.setColor(Color.DARK_GRAY);
                g.fillRect(knobXq, trackYq, knobWq, trackH);
            }

            // served completed (right→left, top→down)
            g.setColor(Color.GREEN);
            List<Passenger> served = engine.getCompletedTicketLines().get(i);
            int startXs = boxX + boxSize + (COLS - 1) * cellW;
            drawGridPartial(g, served,
                            startXs,
                            boxY + (boxSize - gridHeight)/2,
                            cellW, cellW, ROWS,
                            ticketServedOffsets[i],
                            true);

            int fullColsS = (served.size() + ROWS - 1) / ROWS;
            if (fullColsS > COLS) {
                int trackXs = startXs - gridWidth + cellW;
                int trackYs = boxY + (boxSize - gridHeight)/2 + gridHeight + 2;
                int trackWs = COLS * cellW;
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(trackXs, trackYs, trackWs, trackH);
                int knobWs = (int)((COLS/(double)fullColsS)*trackWs);
                int knobXs = trackXs + (int)(ticketServedOffsets[i]/(double)(fullColsS-COLS)*(trackWs-knobWs));
                g.setColor(Color.DARK_GRAY);
                g.fillRect(knobXs, trackYs, knobWs, trackH);
            }
        }

        // Checkpoint lines
        lines = engine.getCheckpointLines().size();
        space = lines > 1 ? (bottom - top) / (lines - 1) : 0;
        for (int i = 0; i < lines; i++) {
            int centerY = space > 0 ? top + i * space : h / 2;
            int boxX = rightX - boxSize / 2, boxY = centerY - boxSize / 2;
            g.setColor(Color.BLACK);
            g.drawRect(boxX, boxY, boxSize, boxSize);

            // queued waiting
            g.setColor(Color.YELLOW);
            // ← now shows the live queue
            List<Passenger> queuedC = engine.getCheckpointLines().get(i);
            int startXc = boxX - cellW;
            drawGridPartial(g, queuedC,
                            startXc,
                            boxY + (boxSize - gridHeight)/2,
                            cellW, cellW, ROWS,
                            checkpointQueuedOffsets[i],
                            true);

            // queued scrollbar
            int fullColsQC = (queuedC.size() + ROWS - 1) / ROWS;
            if (fullColsQC > COLS) {
                int trackXqc = startXc - (COLS - 1) * cellW;
                int trackYqc = boxY + (boxSize - gridHeight)/2 + gridHeight + 2;
                int trackWqc = COLS * cellW;
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(trackXqc, trackYqc, trackWqc, trackH);
                int knobWqc = (int)((COLS/(double)fullColsQC)*trackWqc);
                int knobXqc = trackXqc + (int)(checkpointQueuedOffsets[i]/(double)(fullColsQC-COLS)*(trackWqc-knobWqc));
                g.setColor(Color.DARK_GRAY);
                g.fillRect(knobXqc, trackYqc, knobWqc, trackH);
            }

            // served completed (right→left, top→down)
            g.setColor(Color.GREEN);
            List<Passenger> servedC = engine.getCompletedCheckpointLines().get(i);
            int startXsc = boxX + boxSize + (COLS - 1) * cellW;
            drawGridPartial(g, servedC,
                            startXsc,
                            boxY + (boxSize - gridHeight)/2,
                            cellW, cellW, ROWS,
                            checkpointServedOffsets[i],
                            true);

            int fullColsSC = (servedC.size() + ROWS - 1) / ROWS;
            if (fullColsSC > COLS) {
                int trackXsc = startXsc - gridWidth + cellW;
                int trackYsc = boxY + (boxSize - gridHeight)/2 + gridHeight + 2;
                int trackWsc = COLS * cellW;
                g.setColor(Color.LIGHT_GRAY);
                g.fillRect(trackXsc, trackYsc, trackWsc, trackH);
                int knobWsc = (int)((COLS/(double)fullColsSC)*trackWsc);
                int knobXsc = trackXsc + (int)(checkpointServedOffsets[i]/(double)(fullColsSC-COLS)*(trackWsc-knobWsc));
                g.setColor(Color.DARK_GRAY);
                g.fillRect(knobXsc, trackYsc, knobWsc, trackH);
            }
        }
    }

    /**
     * Draws a sliding window of COLS columns for queued, or right-aligned for served if no scroll needed.
     */
    private void drawGridPartial(Graphics g,
                                 List<Passenger> list,
                                 int startX,
                                 int startY,
                                 int cellW,
                                 int cellH,
                                 int rows,
                                 int offset,
                                 boolean isQueued) {
        int size = list.size();
        int fullCols = (size + rows - 1) / rows;
        int total = Math.min(size, fullCols * rows);
        for (int idx = 0; idx < total; idx++) {
            int row = idx % rows;
            int col = idx / rows;
            int x;
            if (isQueued) {
                int rel = col - offset;
                if (rel < 0 || rel >= COLS) continue;
                x = startX - rel * cellW;
            } else {
                // for served we also use the same "isQueued" logic
                int rel = col - offset;
                if (rel < 0 || rel >= COLS) continue;
                x = startX - rel * cellW;
            }
            int y = startY + row * cellH;
            drawShape(g, list.get(idx).getFlight().getShape(), x, y, cellW, cellH);
            recordClickable(x, y, cellW, cellH, list.get(idx));
        }
    }

    private void drawShape(Graphics g,
                           ShapeType type,
                           int x, int y, int w, int h) {
        switch (type) {
            case CIRCLE:
                g.fillOval(x, y, w, h);
                break;
            case TRIANGLE:
                int[] xs = { x + w/2, x, x + w };
                int[] ys = { y, y + h, y + h };
                g.fillPolygon(xs, ys, 3);
                break;
            case SQUARE:
                g.fillRect(x, y, w, h);
                break;
            default:
                g.fillOval(x, y, w, h);
        }
    }

    private void recordClickable(int x, int y, int w, int h, Passenger p) {
        clickableAreas.add(new Rectangle(x, y, w, h));
        clickablePassengers.add(p);
    }
}
