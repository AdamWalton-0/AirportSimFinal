package sim.ui;

import sim.service.SimulationEngine;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;

/**
 * Live-updating chart of arrivals per interval index.
 * - X axis: interval index (matches timeline slider)
 * - Y axis: total arrivals during that minute-interval
 * - Vertical marker: currently viewed interval
 */
public class ArrivalsGraphPanel extends JPanel {
    private final SimulationEngine engine;

    private final XYSeries series;
    private final XYSeriesCollection dataset;

    private final ValueMarker currentIntervalMarker;
    private final ChartPanel chartPanel;

    // last X value we have plotted (interval index)
    private int plottedMaxInterval = -1;

    public ArrivalsGraphPanel(SimulationEngine engine) {
        super(new BorderLayout());
        this.engine = engine;

        // autoSort=true, allowDuplicateXValues=false
        this.series = new XYSeries("Arrivals", true, false);
        this.dataset = new XYSeriesCollection(series);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Arrivals per Minute Interval",
                "Interval (matches Timeline)",
                "Arrivals",
                dataset,
                PlotOrientation.VERTICAL,
                false,   // legend
                true,    // tooltips
                false    // urls
        );

        XYPlot plot = chart.getXYPlot();

        // vertical marker for current viewed interval
        currentIntervalMarker = new ValueMarker(0);
        plot.addDomainMarker(currentIntervalMarker);

        chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(900, 240));
        chartPanel.setMouseWheelEnabled(true); // nice zoom UX

        add(chartPanel, BorderLayout.CENTER);

        // initial sync
        syncWithEngine();
    }

    /**
     * Call this whenever engine state changes (next/prev/scrub/autorun tick).
     * It will:
     *  - extend the series to maxComputedInterval
     *  - move the marker to engine.getCurrentInterval()
     */
    public void syncWithEngine() {
        int maxComputed = engine.getMaxComputedInterval();

        // append only new points (O(1) per interval)
        for (int i = plottedMaxInterval + 1; i <= maxComputed; i++) {
            int y = engine.getTotalArrivalsAtInterval(i);
            series.add(i, y);
            plottedMaxInterval = i;
        }

        setViewedInterval(engine.getCurrentInterval());
    }

    /**
     * Update marker only (useful while slider is dragging).
     */
    public void setViewedInterval(int intervalIndex) {
        currentIntervalMarker.setValue(intervalIndex);
        chartPanel.repaint();
    }
}
