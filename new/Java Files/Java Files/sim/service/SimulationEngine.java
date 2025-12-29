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
    private final Map<Flight, int[]> minuteArrivalsMap = new HashMap<>();
    private final Map<Flight, Integer> holdRoomCellSize;

    private final int arrivalSpanMinutes;
    private final int intervalMinutes;
    private final int transitDelayMinutes;    // ticket→checkpoint delay
    private final int holdDelayMinutes;       // checkpoint→hold-room delay
    private final int totalIntervals;

    // simulation clock (minutes since globalStart)
    private int currentInterval;

    // restored from before
    private final double percentInPerson;
    private final List<TicketCounterConfig> counterConfigs;
    private final int numCheckpoints;
    private final double checkpointRate;
    private final LocalTime globalStart;
    private final List<Flight> justClosedFlights = new ArrayList<>();
    private final Set<Passenger> ticketCompletedVisible = new HashSet<>();

    private final List<LinkedList<Passenger>> ticketLines;
    private final List<LinkedList<Passenger>> checkpointLines;
    private final List<LinkedList<Passenger>> completedTicketLines;
    private final List<LinkedList<Passenger>> completedCheckpointLines;

    // per-flight counts (needed by clearHistory, etc.)
    private final List<Map<Flight, Integer>> historyArrivals = new ArrayList<>();
    private final List<Map<Flight, Integer>> historyEnqueuedTicket = new ArrayList<>();
    private final List<Map<Flight, Integer>> historyTicketed = new ArrayList<>();
    private final List<Integer> historyTicketLineSize = new ArrayList<>();
    private final List<Map<Flight, Integer>> historyArrivedToCheckpoint = new ArrayList<>();
    private final List<Integer> historyCPLineSize = new ArrayList<>();
    private final List<Map<Flight, Integer>> historyPassedCheckpoint = new ArrayList<>();
    private final List<List<List<Passenger>>> historyOnlineArrivals = new ArrayList<>();
    private final List<List<List<Passenger>>> historyFromTicketArrivals = new ArrayList<>();

    // the hold-room queues
    private final List<LinkedList<Passenger>> holdRoomLines;

    // histories for the UI panels
    private final List<List<List<Passenger>>> historyServedTicket = new ArrayList<>();
    private final List<List<List<Passenger>>> historyQueuedTicket = new ArrayList<>();
    private final List<List<List<Passenger>>> historyServedCheckpoint = new ArrayList<>();
    private final List<List<List<Passenger>>> historyQueuedCheckpoint = new ArrayList<>();
    private final List<List<List<Passenger>>> historyHoldRooms = new ArrayList<>();

    private final Random rand = new Random();

    private double[] counterProgress;
    private double[] checkpointProgress;
    private final Map<Integer, List<Passenger>> pendingToCP;
    private final Map<Integer, List<Passenger>> pendingToHold;
    private Passenger[] counterServing;
    private Passenger[] checkpointServing;

    // ============================
    // PHASES 0–3: REWIND SUPPORT
    // ============================

    /**
     * One snapshot per interval index, where snapshot index == currentInterval value.
     * Index 0 is the initial state before any simulateInterval() has run.
     */
    private final List<EngineSnapshot> stateSnapshots = new ArrayList<>();

    /**
     * The furthest interval index for which we have a snapshot.
     * Always equals stateSnapshots.size() - 1 (when non-empty).
     */
    private int maxComputedInterval = 0;

    /**
     * Immutable state snapshot (deep copies of mutable containers).
     * Passenger objects are referenced (not cloned) intentionally.
     */
    private static final class EngineSnapshot {
        final int currentInterval;

        final List<LinkedList<Passenger>> ticketLines;
        final List<LinkedList<Passenger>> completedTicketLines;
        final List<LinkedList<Passenger>> checkpointLines;
        final List<LinkedList<Passenger>> completedCheckpointLines;
        final List<LinkedList<Passenger>> holdRoomLines;

        final double[] counterProgress;
        final double[] checkpointProgress;

        final Map<Integer, List<Passenger>> pendingToCP;
        final Map<Integer, List<Passenger>> pendingToHold;

        final Passenger[] counterServing;
        final Passenger[] checkpointServing;

        final Set<Passenger> ticketCompletedVisible;
        final List<Flight> justClosedFlights;

        final LinkedHashMap<Integer, Integer> heldUpsByInterval;

        EngineSnapshot(
                int currentInterval,
                List<LinkedList<Passenger>> ticketLines,
                List<LinkedList<Passenger>> completedTicketLines,
                List<LinkedList<Passenger>> checkpointLines,
                List<LinkedList<Passenger>> completedCheckpointLines,
                List<LinkedList<Passenger>> holdRoomLines,
                double[] counterProgress,
                double[] checkpointProgress,
                Map<Integer, List<Passenger>> pendingToCP,
                Map<Integer, List<Passenger>> pendingToHold,
                Passenger[] counterServing,
                Passenger[] checkpointServing,
                Set<Passenger> ticketCompletedVisible,
                List<Flight> justClosedFlights,
                LinkedHashMap<Integer, Integer> heldUpsByInterval
        ) {
            this.currentInterval = currentInterval;
            this.ticketLines = ticketLines;
            this.completedTicketLines = completedTicketLines;
            this.checkpointLines = checkpointLines;
            this.completedCheckpointLines = completedCheckpointLines;
            this.holdRoomLines = holdRoomLines;

            this.counterProgress = counterProgress;
            this.checkpointProgress = checkpointProgress;

            this.pendingToCP = pendingToCP;
            this.pendingToHold = pendingToHold;

            this.counterServing = counterServing;
            this.checkpointServing = checkpointServing;

            this.ticketCompletedVisible = ticketCompletedVisible;
            this.justClosedFlights = justClosedFlights;

            this.heldUpsByInterval = heldUpsByInterval;
        }
    }

    public SimulationEngine(double percentInPerson,
                            List<TicketCounterConfig> counterConfigs,
                            int numCheckpoints,
                            double checkpointRate,
                            int arrivalSpanMinutes,
                            int intervalMinutes,
                            int transitDelayMinutes,
                            int holdDelayMinutes,
                            List<Flight> flights) {
        // assign restored fields
        this.percentInPerson = percentInPerson;
        this.counterConfigs = counterConfigs;
        this.numCheckpoints = numCheckpoints;
        this.checkpointRate = checkpointRate;
        this.arrivalSpanMinutes = arrivalSpanMinutes;
        this.intervalMinutes = intervalMinutes;
        this.transitDelayMinutes = transitDelayMinutes;
        this.holdDelayMinutes = holdDelayMinutes;
        this.flights = flights;

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
        this.totalIntervals = (int) maxClose + 1;

        this.arrivalGenerator = new ArrivalGenerator(arrivalSpanMinutes, intervalMinutes);
        this.minuteGenerator = new ArrivalGenerator(arrivalSpanMinutes, 1);
        for (Flight f : flights) {
            minuteArrivalsMap.put(f, minuteGenerator.generateArrivals(f));
        }

        holdRoomCellSize = new HashMap<>();
        for (Flight f : flights) {
            int total = (int) Math.round(f.getSeats() * f.getFillPercent());
            int bestCell = GridRenderer.MIN_CELL_SIZE;

            // try every possible row-count from 1 up to total:
            for (int rows = 1; rows <= total; rows++) {
                int cols = (total + rows - 1) / rows;           // ceil division
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

        counterProgress = new double[counterConfigs.size()];
        checkpointProgress = new double[numCheckpoints];
        pendingToCP = new HashMap<>();
        pendingToHold = new HashMap<>();
        counterServing = new Passenger[counterConfigs.size()];
        checkpointServing = new Passenger[numCheckpoints];

        // Phase 1: snapshot interval 0 (initial state)
        captureSnapshot0();
    }

    // ============================
    // Phase 1: Capture snapshots
    // ============================

    private void captureSnapshot0() {
        stateSnapshots.clear();
        heldUpsByInterval.clear();
        justClosedFlights.clear();
        ticketCompletedVisible.clear();

        EngineSnapshot s0 = makeSnapshot();
        stateSnapshots.add(s0);
        maxComputedInterval = 0;
    }

    private EngineSnapshot makeSnapshot() {
        return new EngineSnapshot(
                currentInterval,
                deepCopyLinkedLists(ticketLines),
                deepCopyLinkedLists(completedTicketLines),
                deepCopyLinkedLists(checkpointLines),
                deepCopyLinkedLists(completedCheckpointLines),
                deepCopyLinkedLists(holdRoomLines),
                Arrays.copyOf(counterProgress, counterProgress.length),
                Arrays.copyOf(checkpointProgress, checkpointProgress.length),
                deepCopyPendingMap(pendingToCP),
                deepCopyPendingMap(pendingToHold),
                Arrays.copyOf(counterServing, counterServing.length),
                Arrays.copyOf(checkpointServing, checkpointServing.length),
                new HashSet<>(ticketCompletedVisible),
                new ArrayList<>(justClosedFlights),
                new LinkedHashMap<>(heldUpsByInterval)
        );
    }

    private void appendSnapshotAfterInterval() {
        // currentInterval has already been incremented at the end of simulateInterval()
        EngineSnapshot snap = makeSnapshot();

        if (currentInterval < stateSnapshots.size()) {
            stateSnapshots.set(currentInterval, snap);
        } else {
            stateSnapshots.add(snap);
        }
        // Keep the invariant: maxComputedInterval == highest snapshot index we have
        maxComputedInterval = Math.max(maxComputedInterval, currentInterval);
    }

    // ============================
    // Phase 2: Restore snapshots
    // ============================

    private void restoreSnapshot(int targetInterval) {
        int t = clamp(targetInterval, 0, maxComputedInterval);
        EngineSnapshot s = stateSnapshots.get(t);

        // restore clock
        this.currentInterval = s.currentInterval;

        // restore queues IN PLACE (do not replace list objects)
        restoreLinkedListsInPlace(ticketLines, s.ticketLines);
        restoreLinkedListsInPlace(completedTicketLines, s.completedTicketLines);
        restoreLinkedListsInPlace(checkpointLines, s.checkpointLines);
        restoreLinkedListsInPlace(completedCheckpointLines, s.completedCheckpointLines);
        restoreLinkedListsInPlace(holdRoomLines, s.holdRoomLines);

        // restore progress arrays
        if (this.counterProgress == null || this.counterProgress.length != s.counterProgress.length) {
            this.counterProgress = Arrays.copyOf(s.counterProgress, s.counterProgress.length);
        } else {
            System.arraycopy(s.counterProgress, 0, this.counterProgress, 0, s.counterProgress.length);
        }

        if (this.checkpointProgress == null || this.checkpointProgress.length != s.checkpointProgress.length) {
            this.checkpointProgress = Arrays.copyOf(s.checkpointProgress, s.checkpointProgress.length);
        } else {
            System.arraycopy(s.checkpointProgress, 0, this.checkpointProgress, 0, s.checkpointProgress.length);
        }

        // restore pending maps (keep same Map objects; clear+refill)
        this.pendingToCP.clear();
        this.pendingToCP.putAll(deepCopyPendingMap(s.pendingToCP));

        this.pendingToHold.clear();
        this.pendingToHold.putAll(deepCopyPendingMap(s.pendingToHold));

        // restore serving arrays (arrays are internal, but keep shape stable)
        if (this.counterServing == null || this.counterServing.length != s.counterServing.length) {
            this.counterServing = Arrays.copyOf(s.counterServing, s.counterServing.length);
        } else {
            System.arraycopy(s.counterServing, 0, this.counterServing, 0, s.counterServing.length);
        }

        if (this.checkpointServing == null || this.checkpointServing.length != s.checkpointServing.length) {
            this.checkpointServing = Arrays.copyOf(s.checkpointServing, s.checkpointServing.length);
        } else {
            System.arraycopy(s.checkpointServing, 0, this.checkpointServing, 0, s.checkpointServing.length);
        }

        // restore visibility + closures
        this.ticketCompletedVisible.clear();
        this.ticketCompletedVisible.addAll(s.ticketCompletedVisible);

        this.justClosedFlights.clear();
        this.justClosedFlights.addAll(s.justClosedFlights);

        // restore held-ups chart data
        this.heldUpsByInterval.clear();
        this.heldUpsByInterval.putAll(s.heldUpsByInterval);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ============================
    // Phase 3: Public rewind API
    // ============================

    /** True if we can rewind at least one interval. */
    public boolean canRewind() {
        return currentInterval > 0;
    }

    /** True if we can fast-forward using already-computed snapshots (no simulation needed). */
    public boolean canFastForward() {
        return currentInterval < maxComputedInterval;
    }

    /** The largest interval index we have a saved snapshot for. */
    public int getMaxComputedInterval() {
        return maxComputedInterval;
    }

    /** Jump to a specific interval (0..maxComputedInterval) and restore the full state. */
    public void goToInterval(int targetInterval) {
        restoreSnapshot(targetInterval);
    }

    /** Rewind by one interval (if possible). */
    public void rewindOneInterval() {
        if (!canRewind()) return;
        restoreSnapshot(currentInterval - 1);
    }

    /** Fast-forward by one interval if snapshot exists; otherwise simulate forward one step. */
    public void fastForwardOneInterval() {
        if (canFastForward()) {
            restoreSnapshot(currentInterval + 1);
        } else {
            computeNextInterval();
        }
    }

    // ============================
    // Existing API (preserved)
    // ============================

    // === ADVANCE INTERVAL ===
    public void computeNextInterval() {
        if (currentInterval >= totalIntervals) return;

        // IMPORTANT FIX:
        // If we already have the next interval snapshot, always restore it.
        // This makes AutoRun + manual Next reliable after any rewinds/jumps.
        if ((currentInterval + 1) <= maxComputedInterval) {
            restoreSnapshot(currentInterval + 1);
            return;
        }

        // Otherwise, simulate a brand-new interval and append snapshot.
        simulateInterval();
    }

    public void runAllIntervals() {
        // Reset clock + state and re-run
        currentInterval = 0;

        // Clear prior histories + runtime state
        clearHistory();
        heldUpsByInterval.clear();
        justClosedFlights.clear();
        ticketCompletedVisible.clear();
        ticketLines.forEach(LinkedList::clear);
        completedTicketLines.forEach(LinkedList::clear);
        checkpointLines.forEach(LinkedList::clear);
        completedCheckpointLines.forEach(LinkedList::clear);
        holdRoomLines.forEach(LinkedList::clear);
        Arrays.fill(counterProgress, 0);
        Arrays.fill(checkpointProgress, 0);
        pendingToCP.clear();
        pendingToHold.clear();
        Arrays.fill(counterServing, null);
        Arrays.fill(checkpointServing, null);

        // Snapshot interval 0
        captureSnapshot0();

        while (currentInterval < totalIntervals) {
            simulateInterval();
        }
    }

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
                int inPerson = (int) Math.round(totalHere * percentInPerson);
                int online = totalHere - inPerson;

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
        historyHoldRooms.add(deepCopyPassengerLists(holdRoomLines));  // hold-rooms history

        // 7) purge missed passengers
        removeMissedPassengers();

        currentInterval++;

        int stillInTicketQueue = ticketLines.stream().mapToInt(java.util.List::size).sum();
        int stillInCheckpointQueue = checkpointLines.stream().mapToInt(java.util.List::size).sum();
        heldUpsByInterval.put(currentInterval, stillInTicketQueue + stillInCheckpointQueue);

        // snapshot after this interval completes (currentInterval already incremented)
        appendSnapshotAfterInterval();
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

    // deep-copy helper for UI history (unchanged behavior)
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
        historyHoldRooms.clear();

        Arrays.fill(counterProgress, 0);
        Arrays.fill(checkpointProgress, 0);
        pendingToCP.clear();
        pendingToHold.clear();
        ticketCompletedVisible.clear();
        holdRoomLines.forEach(LinkedList::clear);
    }

    // ============================
    // Snapshot copy helpers
    // ============================

    private static List<LinkedList<Passenger>> deepCopyLinkedLists(List<LinkedList<Passenger>> original) {
        List<LinkedList<Passenger>> copy = new ArrayList<>(original.size());
        for (LinkedList<Passenger> line : original) {
            copy.add(new LinkedList<>(line));
        }
        return copy;
    }

    /**
     * Restore list contents without replacing the LinkedList objects.
     * This avoids UI/components holding stale list references.
     */
    private static void restoreLinkedListsInPlace(List<LinkedList<Passenger>> target,
                                                 List<LinkedList<Passenger>> source) {
        if (target.size() != source.size()) {
            // fallback (shouldn't happen): rebuild
            target.clear();
            for (LinkedList<Passenger> src : source) {
                target.add(new LinkedList<>(src));
            }
            return;
        }
        for (int i = 0; i < target.size(); i++) {
            LinkedList<Passenger> t = target.get(i);
            t.clear();
            t.addAll(source.get(i));
        }
    }

    private static Map<Integer, List<Passenger>> deepCopyPendingMap(Map<Integer, List<Passenger>> original) {
        Map<Integer, List<Passenger>> copy = new HashMap<>();
        for (Map.Entry<Integer, List<Passenger>> e : original.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    // === HISTORY GETTERS ===
    public List<List<List<Passenger>>> getHistoryServedTicket() { return historyServedTicket; }
    public List<List<List<Passenger>>> getHistoryQueuedTicket() { return historyQueuedTicket; }
    public List<List<List<Passenger>>> getHistoryOnlineArrivals() { return historyOnlineArrivals; }
    public List<List<List<Passenger>>> getHistoryFromTicketArrivals() { return historyFromTicketArrivals; }
    public List<List<List<Passenger>>> getHistoryServedCheckpoint() { return historyServedCheckpoint; }
    public List<List<List<Passenger>>> getHistoryQueuedCheckpoint() { return historyQueuedCheckpoint; }
    public List<List<List<Passenger>>> getHistoryHoldRooms() { return historyHoldRooms; }

    // === PUBLIC GETTERS ===
    public List<Flight> getFlights() { return flights; }
    public int getArrivalSpan() { return arrivalSpanMinutes; }
    public int getInterval() { return intervalMinutes; }
    public int getTotalIntervals() { return totalIntervals; }
    public int getCurrentInterval() { return currentInterval; }
    public List<LinkedList<Passenger>> getTicketLines() { return ticketLines; }
    public List<LinkedList<Passenger>> getCheckpointLines() { return checkpointLines; }
    public List<LinkedList<Passenger>> getCompletedTicketLines() { return completedTicketLines; }
    public List<LinkedList<Passenger>> getCompletedCheckpointLines() { return completedCheckpointLines; }
    public List<LinkedList<Passenger>> getHoldRoomLines() { return holdRoomLines; }
    public Map<Flight, int[]> getMinuteArrivalsMap() { return Collections.unmodifiableMap(minuteArrivalsMap); }
    public int getTransitDelayMinutes() { return transitDelayMinutes; }
    public int getHoldDelayMinutes() { return holdDelayMinutes; }

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
        return new LinkedHashMap<>(heldUpsByInterval); // protect original
    }

    // === ARRIVALS METRICS (for live graph) ===

    /**
    * Total passenger arrivals at a specific minute since globalStart.
    * This matches the same indexing logic used in simulateInterval().
    */
    public int getTotalArrivalsAtMinute(int minuteSinceGlobalStart) {
        int sum = 0;

        for (Flight f : flights) {
            int[] perMin = minuteArrivalsMap.get(f);
            if (perMin == null) continue;

            long offset = Duration.between(
                    globalStart,
                    f.getDepartureTime().minusMinutes(arrivalSpanMinutes)
            ).toMinutes();

            int idx = minuteSinceGlobalStart - (int) offset;
            if (idx >= 0 && idx < perMin.length) {
                sum += perMin[idx];
            }
        }
        return sum;
    }

    /**
    * Arrivals per "interval index" so it aligns with your timeline slider:
    * interval 0 = initial (no work done yet) -> 0 arrivals
    * interval i (>=1) corresponds to arrivals that happened during minute (i-1).
    */
    public int getTotalArrivalsAtInterval(int intervalIndex) {
        if (intervalIndex <= 0) return 0;
        return getTotalArrivalsAtMinute(intervalIndex - 1);
    }

}
