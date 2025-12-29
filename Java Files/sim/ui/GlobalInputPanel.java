package sim.ui;

import javax.swing.*;
import java.awt.*;

public class GlobalInputPanel extends JPanel {
    private final JTextField percentInPersonField;
    private final JTextField checkpointsField;
    private final JTextField rateCheckpointField;
    private final JTextField arrivalSpanField;
    private final JTextField transitDelayField;
    private final JTextField holdroomDelayField;    // ✱ new
    private final JTextField intervalField;

    public GlobalInputPanel() {
        // now 7 rows instead of 6
        setLayout(new GridLayout(7, 2, 5, 5));

        percentInPersonField = addLabeledField("% In Person (0-1):");
        checkpointsField     = addLabeledField("# of Checkpoints:");
        rateCheckpointField  = addLabeledField("Rate / Checkpoint (per min):");
        arrivalSpanField     = addLabeledField("Arrival Span (min):");
        transitDelayField    = addLabeledField("Transit Delay (min):");
        holdroomDelayField   = addLabeledField("Hold-room Delay (min):");  // ✱
        intervalField        = addLabeledField("Interval (min):");

        // defaults
        percentInPersonField.setText("0.4");
        checkpointsField.setText("1");
        rateCheckpointField.setText("1");
        arrivalSpanField.setText("120");
        transitDelayField.setText("2");
        holdroomDelayField.setText("5");  // default hold-room delay

        // force interval = 1 and disable editing
        intervalField.setText("1");
        intervalField.setEnabled(true);
        intervalField.setEditable(false);
intervalField.setFocusable(false);
intervalField.setBackground(new Color(200, 200, 200)); // medium gray
intervalField.setForeground(Color.BLACK); // keep text readable

        
    }

    private JTextField addLabeledField(String label) {
        add(new JLabel(label));
        JTextField field = new JTextField();
        add(field);
        return field;
    }

    public int getHoldroomDelayMinutes() {
        return Integer.parseInt(holdroomDelayField.getText());
    }

    public double getPercentInPerson()   { return Double.parseDouble(percentInPersonField.getText()); }
    public int    getNumCheckpoints()    { return Integer.parseInt(checkpointsField.getText()); }
    public double getRatePerCheckpoint() { return Double.parseDouble(rateCheckpointField.getText()); }
    public int    getArrivalSpanMinutes(){ return Integer.parseInt(arrivalSpanField.getText()); }
    public int    getTransitDelayMinutes(){return Integer.parseInt(transitDelayField.getText());}
    /** Always returns 1 */
    public int    getIntervalMinutes()   { return 1; }
}
