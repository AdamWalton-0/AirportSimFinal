package sim.ui;


import sim.model.Flight;
import java.util.HashSet;
import java.util.Set;


/**
 * Configuration for a single ticket counter:
 * - id: sequential counter number
 * - rate: passengers served per minute
 * - allowedFlights: which flights this counter accepts (empty = all)
 */
public class TicketCounterConfig {
    private int id;
    private double rate;
    private Set<Flight> allowedFlights;


    /** Full constructor: supply id, rate, and explicit set (empty = all) */
    public TicketCounterConfig(int id, double rate, Set<Flight> allowedFlights) {
        this.id = id;
        this.rate = rate;
        // copy to avoid external mutation
        this.allowedFlights = new HashSet<>(allowedFlights);
    }


    /** Default: rate=1.0, accepts all flights (empty set) */
    public TicketCounterConfig(int id) {
        this(id, 1.0, new HashSet<>());
    }


    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }


    public double getRate() {
        return rate;
    }
    public void setRate(double rate) {
        this.rate = rate;
    }


    public Set<Flight> getAllowedFlights() {
        return allowedFlights;
    }
    public void setAllowedFlights(Set<Flight> allowedFlights) {
        this.allowedFlights = new HashSet<>(allowedFlights);
    }


    /** true if no restrictions (empty = all flights) */
    public boolean isAllFlights() {
        return allowedFlights.isEmpty();
    }


    /** true if this counter will accept passengers for flight f */
    public boolean accepts(Flight f) {
        return isAllFlights() || allowedFlights.contains(f);
    }


    @Override
    public String toString() {
        if (isAllFlights()) return "All flights";
        StringBuilder sb = new StringBuilder();
        for (Flight f : allowedFlights) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(f.getFlightNumber());
        }
        return sb.toString();
    }
}
