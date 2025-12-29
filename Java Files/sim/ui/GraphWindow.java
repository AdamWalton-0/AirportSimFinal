package sim.ui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class GraphWindow extends JFrame {

    public GraphWindow(String title, Map<Integer, Integer> heldUpData) {
        super(title);

        // Create dataset from simulation data
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<Integer, Integer> entry : heldUpData.entrySet()) {
            dataset.addValue(entry.getValue(), "Held Up", "Interval " + entry.getKey());
        }

        // Create chart
        JFreeChart chart = ChartFactory.createBarChart(
                "Passenger Hold-Ups by Interval",
                "Interval (min)",
                "Passengers Held",
                dataset,
                PlotOrientation.VERTICAL,
                false, true, false
        );

        // Set up chart panel
        ChartPanel panel = new ChartPanel(chart);
        panel.setPreferredSize(new Dimension(600, 400));
        setContentPane(panel);

        pack();
        setLocationRelativeTo(null);
    }
}
