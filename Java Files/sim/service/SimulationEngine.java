package sim.service;

import sim.model.Flight;
import sim.model.Passenger;
import sim.ui.GridRenderer;
import sim.ui.TicketCounterConfig;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

public class SimulationEngine {
    private final List<Flight> flights;
    private final Map<Integer, Integer> heldUpsByInterval = new LinkedHashMap<>();
    private final ArrivalGenerator arrivalGenerator;
    private final ArrivalGenerator minuteGenerator;
    private final Map<Flight,int[]> minuteArrivalsMap = new HashMap<>();
    private final Map<Flight,Integer> holdRoomCellSize;

    private final int arrivalSpanMinutes;
    private final int intervalMinutes;
    private final int transitDelayMinutes;    // ticket→checkpoint delay
    private final int holdDelayMinutes;       // checkpoint→hold-room delay
    private final int totalIntervals;

    // ← ADD THIS BACK IN:
    private int currentInterval;

    // restored from before
    private final double                         percentInPerson;
    private final List<TicketCounterConfig>      counterConfigs;
    private final int                            numCheckpoints;
    private final double                         checkpointRate;
    private final LocalTime                      globalStart;
    private final List<Flight>                   justClosedFlights      = new ArrayList<>();
    private final Set<Passenger>                 ticketCompletedVisible = new HashSet<>();

    private final List<LinkedList<Passenger>>    ticketLines;
    private final List<LinkedList<Passenger>>    checkpointLines;
    private final List<LinkedList<Passenger>>    completedTicketLines;
    private final List<LinkedList<Passenger>>    completedCheckpointLines;

    // per-flight counts (needed by clearHistory, etc.)
    private final List<Map<Flight,Integer>>      historyArrivals             = new ArrayList<>();
    private final List<Map<Flight,Integer>>      historyEnqueuedTicket       = new ArrayList<>();
    private final List<Map<Flight,Integer>>      historyTicketed             = new ArrayList<>();
    private final List<Integer>                  historyTicketLineSize       = new ArrayList<>();
    private final List<Map<Flight,Integer>>      historyArrivedToCheckpoint  = new ArrayList<>();
    private final List<Integer>                  historyCPLineSize           = new ArrayList<>();
    private final List<Map<Flight,Integer>>      historyPassedCheckpoint     = new ArrayList<>();
    private final List<List<List<Passenger>>>    historyOnlineArrivals       = new ArrayList<>();
    private final List<List<List<Passenger>>>    historyFromTicketArrivals   = new ArrayList<>();

    // the hold-room queues
    private final List<LinkedList<Passenger>>    holdRoomLines;

    // histories for the UI panels
    private final List<List<List<Passenger>>>    historyServedTicket     = new ArrayList<>();
    private final List<List<List<Passenger>>>    historyQueuedTicket     = new ArrayList<>();
    private final List<List<List<Passenger>>>    historyServedCheckpoint = new ArrayList<>();
    private final List<List<List<Passenger>>>    historyQueuedCheckpoint = new ArrayList<>();
    private final List<List<List<Passenger>>>    historyHoldRooms        = new ArrayList<>();

    private final Random                         rand = new Random();

    private double[]                             counterProgress;
    private double[]                             checkpointProgress;
    private Map<Integer, List<Passenger>>        pendingToCP;
    private Map<Integer, List<Passenger>>        pendingToHold;
    private Passenger[]                          counterServing;
    private Passenger[]                          checkpointServing;

    public SimulationEngine(double percentInPerson,
                            List<TicketCounterConfig> counterConfigs,
                            int numCheckpoints,
                            double checkpointRate,
                            int arrivalSpanMinutes,
                            int intervalMinutes,
                            int transitDelayMinutes,
                            int holdDelayMinutes,
                            List<Flight> flights) {
        // ← Edit 1: assign restored fields
        this.percentInPerson      = percentInPerson;
        this.counterConfigs       = counterConfigs;
        this.numCheckpoints       = numCheckpoints;
        this.checkpointRate       = checkpointRate;
        this.arrivalSpanMinutes   = arrivalSpanMinutes;
        this.intervalMinutes      = intervalMinutes;
        this.transitDelayMinutes  = transitDelayMinutes;
        this.holdDelayMinutes     = holdDelayMinutes;
        this.flights              = flights;

        // compute global start time based on earliest departure
        LocalTime firstDep = flights.stream()
            .map(Flight::getDepartureTime)
            .min(LocalTime::compareTo)
            .orElse(LocalTime.MIDNIGHT);
        this.globalStart = firstDep.minusMinutes(arrivalSpanMinutes);

        // compute total intervals up to the latest boarding-close (depTime - 20)
        long maxClose = flights.stream()
            .mapToLong(f -> Duration.between(
                globalStart,
                f.getDepartureTime().minusMinutes(20)
            ).toMinutes())
            .max().orElse(0);
        this.totalIntervals = (int)maxClose + 1;

        this.arrivalGenerator = new ArrivalGenerator(arrivalSpanMinutes, intervalMinutes);
        this.minuteGenerator  = new ArrivalGenerator(arrivalSpanMinutes, 1);
        for (Flight f : flights) {
            minuteArrivalsMap.put(f, minuteGenerator.generateArrivals(f));
        }

        holdRoomCellSize = new HashMap<>();

        for (Flight f : flights) {
            int total = (int)Math.round(f.getSeats() * f.getFillPercent());
            int bestCell = GridRenderer.MIN_CELL_SIZE;

            // try every possible row-count from 1 up to total:
            for (int rows = 1; rows <= total; rows++) {
                int cols = (total + rows - 1) / rows;           // ceil division
                // cell-size that would exactly span both rows vertically
                // and columns horizontally
                int cellByRows = GridRenderer.HOLD_BOX_SIZE / rows;
                int cellByCols = GridRenderer.HOLD_BOX_SIZE / cols;
                int cell = Math.min(cellByRows, cellByCols);
                bestCell = Math.max(bestCell, cell);
            }

    holdRoomCellSize.put(f, bestCell);
}
        this.currentInterval = 0;

        // ticket lines
        ticketLines = new ArrayList<>();
        completedTicketLines = new ArrayList<>();
        for (int i = 0; i < counterConfigs.size(); i++) {
            ticketLines.add(new LinkedList<>());
            completedTicketLines.add(new LinkedList<>());
        }

        // checkpoint lines
        checkpointLines = new ArrayList<>();
        completedCheckpointLines = new ArrayList<>();
        for (int i = 0; i < numCheckpoints; i++) {
            checkpointLines.add(new LinkedList<>());
            completedCheckpointLines.add(new LinkedList<>());
        }

        // hold-room lines (one per flight)
        holdRoomLines = new ArrayList<>();
        for (int i = 0; i < flights.size(); i++) {
            holdRoomLines.add(new LinkedList<>());
        }

        counterProgress    = new double[counterConfigs.size()];
        checkpointProgress = new double[numCheckpoints];
        pendingToCP        = new HashMap<>();
        pendingToHold      = new HashMap<>();
        counterServing     = new Passenger[counterConfigs.size()];
        checkpointServing  = new Passenger[numCheckpoints];
    }

    // === ADVANCE INTERVAL ===
    public void computeNextInterval() {
        if (currentInterval < totalIntervals) simulateInterval();
    }

    public void runAllIntervals() {
        currentInterval = 0;
        clearHistory();
        while (currentInterval < totalIntervals) simulateInterval();
    }

    // ... now your existing simulateInterval() follows ...



    // === MAIN SIMULATION STEP ===
    public void simulateInterval() {
        // clear previous tick closures
        justClosedFlights.clear();

        int minute = currentInterval; // minutes since globalStart

        // 1) arrivals & boarding-close (unchanged)
        for (Flight f : flights) {
            int[] perMin = minuteArrivalsMap.get(f);
            long offset = Duration.between(globalStart,
                                            f.getDepartureTime().minusMinutes(arrivalSpanMinutes))
                                    .toMinutes();
            int idx = minute - (int) offset;
            if (idx >= 0 && idx < perMin.length) {
                int totalHere = perMin[idx];
                int inPerson  = (int) Math.round(totalHere * percentInPerson);
                int online    = totalHere - inPerson;

                // choose counters accepting this flight
                List<Integer> allowed = new ArrayList<>();
                for (int j = 0; j < counterConfigs.size(); j++) {
                    if (counterConfigs.get(j).accepts(f)) {
                        allowed.add(j);
                    }
                }
                if (allowed.isEmpty()) {
                    for (int j = 0; j < counterConfigs.size(); j++) {
                        allowed.add(j);
                    }
                }

                // enqueue in-person
                for (int i = 0; i < inPerson; i++) {
                    Passenger p = new Passenger(f, minute, true);
                    int best = allowed.get(0);
                    for (int ci : allowed) {
                        if (ticketLines.get(ci).size() < ticketLines.get(best).size()) {
                            best = ci;
                        }
                    }
                    ticketLines.get(best).add(p);
                }
                // online → checkpoint
                for (int i = 0; i < online; i++) {
                    Passenger p = new Passenger(f, minute, false);
                    p.setCheckpointEntryMinute(minute);
                    int bestC = 0;
                    for (int j = 1; j < numCheckpoints; j++) {
                        if (checkpointLines.get(j).size()
                            < checkpointLines.get(bestC).size()) {
                            bestC = j;
                        }
                    }
                    checkpointLines.get(bestC).add(p);
                }
            }

            // boarding-close detection
            int closeIdx = (int) Duration.between(globalStart,
                                                    f.getDepartureTime().minusMinutes(20))
                                            .toMinutes();
            if (minute == closeIdx) {
                justClosedFlights.add(f);
                ticketLines.forEach(line ->
                    line.stream()
                        .filter(p -> p.getFlight() == f)
                        .forEach(p -> p.setMissed(true))
                );
                completedTicketLines.forEach(line ->
                    line.stream()
                        .filter(p -> p.getFlight() == f)
                        .forEach(p -> p.setMissed(true))
                );
                checkpointLines.forEach(line ->
                    line.stream()
                        .filter(p -> p.getFlight() == f)
                        .forEach(p -> p.setMissed(true))
                );
                completedCheckpointLines.forEach(line ->
                    line.stream()
                        .filter(p -> p.getFlight() == f)
                        .forEach(p -> p.setMissed(true))
                );

            }
        }

        // 2) ticket-counter service
        for (int c = 0; c < counterConfigs.size(); c++) {
            double rate = counterConfigs.get(c).getRate();
            counterProgress[c] += rate;
            int toComplete = (int) Math.floor(counterProgress[c]);
            counterProgress[c] -= toComplete;

            for (int k = 0; k < toComplete; k++) {
                if (counterServing[c] == null && !ticketLines.get(c).isEmpty()) {
                    counterServing[c] = ticketLines.get(c).poll();
                }
                if (counterServing[c] == null) break;

                Passenger done = counterServing[c];
                done.setTicketCompletionMinute(minute);
                completedTicketLines.get(c).add(done);
                ticketCompletedVisible.add(done);
                pendingToCP.computeIfAbsent(minute + transitDelayMinutes, x -> new ArrayList<>())
                            .add(done);
                counterServing[c] = null;
            }
        }

        // 3) move from ticket → checkpoint
        List<Passenger> toMove = pendingToCP.remove(minute);
        if (toMove != null) {
            for (Passenger p : toMove) {
                ticketCompletedVisible.remove(p);
                p.setCheckpointEntryMinute(minute);
                int bestC = 0;
                for (int j = 1; j < numCheckpoints; j++) {
                    if (checkpointLines.get(j).size()
                        < checkpointLines.get(bestC).size()) {
                        bestC = j;
                    }
                }
                checkpointLines.get(bestC).add(p);
            }
        }

        // 4) checkpoint service & schedule hold-room
        for (int c = 0; c < numCheckpoints; c++) {
            checkpointProgress[c] += checkpointRate;
            int toComplete = (int) Math.floor(checkpointProgress[c]);
            checkpointProgress[c] -= toComplete;

            for (int k = 0; k < toComplete; k++) {
                if (checkpointServing[c] == null && !checkpointLines.get(c).isEmpty()) {
                    checkpointServing[c] = checkpointLines.get(c).poll();
                }
                if (checkpointServing[c] == null) break;

                Passenger done = checkpointServing[c];
                done.setCheckpointCompletionMinute(minute);
                completedCheckpointLines.get(c).add(done);
                // schedule into hold-room
                pendingToHold.computeIfAbsent(minute + holdDelayMinutes, x -> new ArrayList<>())
                            .add(done);
                checkpointServing[c] = null;
            }
        }

        // 5) move from checkpoint → hold-room
        List<Passenger> toHold = pendingToHold.remove(minute);
            if (toHold != null) {
                for (Passenger p : toHold) {
                    // compute this flight's boarding-close interval
                    int closeIdx = (int) Duration.between(
                        globalStart,
                        p.getFlight().getDepartureTime().minusMinutes(20)
                    ).toMinutes();

                    if (minute <= closeIdx) {
                        // still open: enqueue as before
                        p.setHoldRoomEntryMinute(minute);
                        int idx = flights.indexOf(p.getFlight());
                        int seq = holdRoomLines.get(idx).size() + 1;
                        p.setHoldRoomSequence(seq);
                        holdRoomLines.get(idx).add(p);
                    } else {
                        // boarding closed → mark missed so removeMissedPassengers will purge
                        p.setMissed(true);
                    }
                }
            }

        // 6) record history for UI
        historyServedTicket.add(deepCopyPassengerLists(completedTicketLines));
        historyQueuedTicket.add(deepCopyPassengerLists(ticketLines));
        historyServedCheckpoint.add(deepCopyPassengerLists(completedCheckpointLines));
        historyQueuedCheckpoint.add(deepCopyPassengerLists(checkpointLines));
        historyHoldRooms.add(deepCopyPassengerLists(holdRoomLines));  // ← NEW

        // 7) purge missed passengers
        removeMissedPassengers();

        currentInterval++;
        
        int stillInTicketQueue = ticketLines.stream().mapToInt(java.util.List::size).sum();
int stillInCheckpointQueue = checkpointLines.stream().mapToInt(java.util.List::size).sum();
heldUpsByInterval.put(currentInterval, stillInTicketQueue + stillInCheckpointQueue);
    }

    // === ACCESSORS & UTILITY ===

    /** flights whose boarding closed this tick */
    public List<Flight> getFlightsJustClosed() {
        return new ArrayList<>(justClosedFlights);
    }

    public void removeMissedPassengers() {
        ticketLines.forEach(line -> line.removeIf(Passenger::isMissed));
        completedTicketLines.forEach(line -> line.removeIf(Passenger::isMissed));
        checkpointLines.forEach(line -> line.removeIf(Passenger::isMissed));
        completedCheckpointLines.forEach(line -> line.removeIf(Passenger::isMissed));
    }

    // deep-copy helper
    private List<List<Passenger>> deepCopyPassengerLists(List<LinkedList<Passenger>> original) {
        List<List<Passenger>> copy = new ArrayList<>();
        for (LinkedList<Passenger> line : original) {
            copy.add(new ArrayList<>(line));
        }
        return copy;
    }

    // === CLEAR HISTORY ===
    private void clearHistory() {
        historyArrivals.clear();
        historyEnqueuedTicket.clear();
        historyTicketed.clear();
        historyTicketLineSize.clear();
        historyArrivedToCheckpoint.clear();
        historyCPLineSize.clear();
        historyPassedCheckpoint.clear();
        historyServedTicket.clear();
        historyQueuedTicket.clear();
        historyOnlineArrivals.clear();
        historyFromTicketArrivals.clear();
        historyServedCheckpoint.clear();
        historyQueuedCheckpoint.clear();
        historyHoldRooms.clear();      // ← NEW

        Arrays.fill(counterProgress, 0);
        Arrays.fill(checkpointProgress, 0);
        pendingToCP.clear();
        pendingToHold.clear();         // ← NEW
        ticketCompletedVisible.clear();
        holdRoomLines.forEach(LinkedList::clear);  // ← NEW
    }

    // === HISTORY GETTERS ===
    public List<List<List<Passenger>>> getHistoryServedTicket()    { return historyServedTicket; }
    public List<List<List<Passenger>>> getHistoryQueuedTicket()    { return historyQueuedTicket; }
    public List<List<List<Passenger>>> getHistoryOnlineArrivals()  { return historyOnlineArrivals; }
    public List<List<List<Passenger>>> getHistoryFromTicketArrivals() { return historyFromTicketArrivals; }
    public List<List<List<Passenger>>> getHistoryServedCheckpoint() { return historyServedCheckpoint; }
    public List<List<List<Passenger>>> getHistoryQueuedCheckpoint() { return historyQueuedCheckpoint; }
    public List<List<List<Passenger>>> getHistoryHoldRooms()       { return historyHoldRooms; }  // ← NEW

    // === PUBLIC GETTERS ===
    public List<Flight> getFlights()                         { return flights; }
    public int getArrivalSpan()                              { return arrivalSpanMinutes; }
    public int getInterval()                                 { return intervalMinutes; }
    public int getTotalIntervals()                           { return totalIntervals; }
    public int getCurrentInterval()                          { return currentInterval; }
    public List<LinkedList<Passenger>> getTicketLines()      { return ticketLines; }
    public List<LinkedList<Passenger>> getCheckpointLines()  { return checkpointLines; }
    public List<LinkedList<Passenger>> getCompletedTicketLines()     { return completedTicketLines; }
    public List<LinkedList<Passenger>> getCompletedCheckpointLines() { return completedCheckpointLines; }
    public List<LinkedList<Passenger>> getHoldRoomLines()    { return holdRoomLines; }     // ← NEW
    public Map<Flight,int[]> getMinuteArrivalsMap()          { return Collections.unmodifiableMap(minuteArrivalsMap); }
    public int getTransitDelayMinutes()                      { return transitDelayMinutes; }
    public int getHoldDelayMinutes()                         { return holdDelayMinutes; }  // ← NEW
    public int getHoldRoomCellSize(Flight f) {
    return holdRoomCellSize.getOrDefault(f, GridRenderer.MIN_CELL_SIZE);
}

    public List<TicketCounterConfig> getCounterConfigs() {
        return Collections.unmodifiableList(counterConfigs);
    }
    public List<Passenger> getVisibleCompletedTicketLine(int idx) {
        List<Passenger> visible = new ArrayList<>();
        for (Passenger p : completedTicketLines.get(idx)) {
            if (ticketCompletedVisible.contains(p)) {
                visible.add(p);
            }
        }
        return visible;
    }
    public List<Passenger> getCheckpointLine() {
        List<Passenger> all = new ArrayList<>();
        for (LinkedList<Passenger> line : checkpointLines) {
            all.addAll(line);
        }
        return all;
    }
    public Map<Integer, Integer> getHoldUpsByInterval() {
    return new LinkedHashMap<>(heldUpsByInterval); // return a copy to protect original
}

}


