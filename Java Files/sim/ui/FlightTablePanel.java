package sim.ui;


// FlightTablePanel.java


import sim.model.Flight;
import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.time.LocalTime;
import java.util.List;


public class FlightTablePanel extends JPanel {
    private JTable table;
    private FlightTableModel model;
    private static int flightCounter = 1;


    public FlightTablePanel() {
        setLayout(new BorderLayout());
        model = new FlightTableModel();
        table = new JTable(model);
        TableColumn shapeCol = table.getColumnModel().getColumn(4);
        JComboBox<Flight.ShapeType> combo = new JComboBox<>(Flight.ShapeType.values());
        shapeCol.setCellEditor(new DefaultCellEditor(combo));


        add(new JScrollPane(table), BorderLayout.CENTER);
        JButton addBtn = new JButton("Add Flight");
        JButton removeBtn = new JButton("Remove Flight");
 // put this at the top of the class


addBtn.addActionListener(e -> model.addFlight(
    new Flight(
        String.valueOf(flightCounter++),
        LocalTime.now(),
        180,          // seats
        0.85,         // fill %
        Flight.ShapeType.CIRCLE
    )
));


        removeBtn.addActionListener(e -> {
            int sel = table.getSelectedRow();
            if (sel >= 0) model.removeFlight(sel);
        });
        JPanel btnPanel = new JPanel();
        btnPanel.add(addBtn);
        btnPanel.add(removeBtn);
        add(btnPanel, BorderLayout.SOUTH);
    }


    public List<Flight> getFlights() { return model.getFlights(); }
}
