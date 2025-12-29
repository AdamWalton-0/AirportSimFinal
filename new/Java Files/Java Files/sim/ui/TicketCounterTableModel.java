package sim.ui;


import sim.model.Flight;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * Table model for configuring ticket counters:
 * Columns: [Counter #, Rate (per min), Available Flights]
 */
public class TicketCounterTableModel extends AbstractTableModel {
    private final String[] columns = {
        "Counter #", "Rate (per min)", "Available Flights"
    };
    private final List<TicketCounterConfig> counters;
    private final List<Flight> flights;


    public TicketCounterTableModel(List<Flight> flights) {
        this.flights = flights;
        this.counters = new ArrayList<>();
    }


    @Override
    public int getRowCount() {
        return counters.size();
    }


    @Override
    public int getColumnCount() {
        return columns.length;
    }


    @Override
    public String getColumnName(int col) {
        return columns[col];
    }


    @Override
    public Class<?> getColumnClass(int col) {
        switch (col) {
            case 0: return Integer.class;
            case 1: return Double.class;
            case 2: return String.class;
            default: return Object.class;
        }
    }


    @Override
    public Object getValueAt(int row, int col) {
        TicketCounterConfig cfg = counters.get(row);
        switch (col) {
            case 0: return cfg.getId();
            case 1: return cfg.getRate();
            case 2:
                // show "All" when no restrictions
                return cfg.isAllFlights()
                    ? "All"
                    : String.join(", ",
                        cfg.getAllowedFlights().stream()
                           .map(Flight::getFlightNumber)
                           .toArray(String[]::new)
                      );
            default:
                return null;
        }
    }


    @Override
    public boolean isCellEditable(int row, int col) {
        // only rate and flight‐selection are editable
        return col == 1 || col == 2;
    }


    @Override
    @SuppressWarnings("unchecked")
    public void setValueAt(Object val, int row, int col) {
        TicketCounterConfig cfg = counters.get(row);
        switch (col) {
            case 1:
                cfg.setRate((Double) val);
                break;
            case 2:
                if (val instanceof Set) {
                    cfg.setAllowedFlights((Set<Flight>) val);
                }
                break;
            default:
                return;
        }
        fireTableCellUpdated(row, col);
    }


    /** Adds a new counter with default settings. */
    public void addCounter() {
        int id = counters.size() + 1;
        counters.add(new TicketCounterConfig(id));
        fireTableRowsInserted(counters.size() - 1, counters.size() - 1);
    }


    /** Removes the counter and reassigns IDs. */
    public void removeCounter(int idx) {
        counters.remove(idx);
        for (int i = 0; i < counters.size(); i++) {
            counters.get(i).setId(i + 1);
        }
        fireTableDataChanged();
    }


    /** Returns the user‐configured counters. */
    public List<TicketCounterConfig> getCounters() {
        return new ArrayList<>(counters);
    }
}
