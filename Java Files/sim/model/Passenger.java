package sim.model;

public class Passenger {
    private final Flight flight;
    private final int arrivalMinute;            // minute they arrived at airport
    private final boolean inPerson;             // true = bought in person, false = online

    private int ticketCompletionMinute;         // minute they finished at ticket counter
    private int checkpointEntryMinute;          // minute they entered checkpoint queue
    private int checkpointCompletionMinute;     // minute they finished at checkpoint

    // Tracks if passenger missed their flight (boarding closed before checkpoint completion)
    private boolean missed = false;

    // hold-room entry minute (relative to start) and arrival order
    private int holdRoomEntryMinute  = -1;
    private int holdRoomSequence     = -1;
    /**
     * Old‑style constructor: defaults to in‑person, unknown minute
     */
    public Passenger(Flight flight) {
        this(flight, -1, true);
    }

    /** When did they arrive in the hold-room? */
    public int getHoldRoomEntryMinute() {
        return holdRoomEntryMinute;
    }
    public void setHoldRoomEntryMinute(int m) {
        this.holdRoomEntryMinute = m;
    }

    /** What number were they in arrival order to the hold-room? */
    public int getHoldRoomSequence() {
        return holdRoomSequence;
    }
    public void setHoldRoomSequence(int seq) {
        this.holdRoomSequence = seq;
    }

    /**
     * Legacy constructor with arrivalMinute: defaults to in‑person
     */
    public Passenger(Flight flight, int arrivalMinute) {
        this(flight, arrivalMinute, true);
    }

    /**
     * New full constructor: specify arrivalMinute *and* whether in person
     */
    public Passenger(Flight flight, int arrivalMinute, boolean inPerson) {
        this.flight         = flight;
        this.arrivalMinute  = arrivalMinute;
        this.inPerson       = inPerson;
    }

    /** @return the flight this passenger is on */
    public Flight getFlight() {
        return flight;
    }

    /** @return minute they arrived at the airport (relative to schedule start) */
    public int getArrivalMinute() {
        return arrivalMinute;
    }

    /** @return true if this passenger bought their ticket in person */
    public boolean isInPerson() {
        return inPerson;
    }

    /**
     * @return Minute when this passenger finished service at the ticket counter
     */
    public int getTicketCompletionMinute() {
        return ticketCompletionMinute;
    }

    public void setTicketCompletionMinute(int ticketCompletionMinute) {
        this.ticketCompletionMinute = ticketCompletionMinute;
    }

    /**
     * @return Minute when this passenger entered the checkpoint queue
     */
    public int getCheckpointEntryMinute() {
        return checkpointEntryMinute;
    }

    public void setCheckpointEntryMinute(int checkpointEntryMinute) {
        this.checkpointEntryMinute = checkpointEntryMinute;
    }

    /**
     * @return Minute when this passenger finished service at the checkpoint
     */
    public int getCheckpointCompletionMinute() {
        return checkpointCompletionMinute;
    }

    public void setCheckpointCompletionMinute(int checkpointCompletionMinute) {
        this.checkpointCompletionMinute = checkpointCompletionMinute;
    }

    /**
     * Mark passenger as missed when boarding closes before checkpoint completion
     * @param missed true if passenger missed their flight
     */
    public void setMissed(boolean missed) {
        this.missed = missed;
    }

    /**
     * Check if passenger missed their flight
     * @return true if passenger missed flight, otherwise false
     */
    public boolean isMissed() {
        return missed;
    }
}
