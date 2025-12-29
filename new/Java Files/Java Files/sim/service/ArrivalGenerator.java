// --- ArrivalGenerator.java ---
package sim.service;


import sim.model.Flight;
import java.util.*;


public class ArrivalGenerator {
    private final int totalMinutes;       // total minutes from arrival start to cutoff
    private final int intervalMinutes;
    private final double[] minuteProbabilities;


    public ArrivalGenerator(int arrivalSpanMinutes, int intervalMinutes) {
        // arrivals span from (departure - arrivalSpan) up to departure - 20
        this.totalMinutes = arrivalSpanMinutes - 20;
        this.intervalMinutes = intervalMinutes;


        // build a minute-by-minute probability distribution (normalized PDF)
        minuteProbabilities = new double[totalMinutes];
        double mean = totalMinutes / 2.0;
        double sigma = totalMinutes / 6.0;
        double sum = 0;
        for (int m = 0; m < totalMinutes; m++) {
            double x = (m + 0.5 - mean) / sigma;
            double pdf = Math.exp(-0.5 * x * x);
            minuteProbabilities[m] = pdf;
            sum += pdf;
        }
        for (int m = 0; m < totalMinutes; m++) {
            minuteProbabilities[m] /= sum;
        }
    }


    /**
     * Returns an array of length totalMinutes where each entry is the exact
     * number of arrivals in that minute (summing to totalPassengers).
     */
    public int[] generatePerMinuteArrivals(Flight flight) {
        int totalPassengers = (int) Math.round(flight.getSeats() * flight.getFillPercent());
        int[] arrivals = new int[totalMinutes];
        double[] raw = new double[totalMinutes];
        int floorSum = 0;
        for (int m = 0; m < totalMinutes; m++) {
            raw[m] = minuteProbabilities[m] * totalPassengers;
            arrivals[m] = (int) Math.floor(raw[m]);
            floorSum += arrivals[m];
        }
        int remainder = totalPassengers - floorSum;
        List<Integer> idx = new ArrayList<>();
        for (int m = 0; m < totalMinutes; m++) idx.add(m);
        idx.sort((a, b) -> Double.compare(raw[b] - arrivals[b], raw[a] - arrivals[a]));
        for (int k = 0; k < remainder; k++) {
            arrivals[idx.get(k)]++;
        }
        return arrivals;
    }


    /**
     * Aggregates per-minute arrivals into interval buckets.
     * Returns an int[] of length (totalMinutes/intervalMinutes).
     */
    public int[] generateArrivals(Flight flight) {
        int[] minuteArr = generatePerMinuteArrivals(flight);
        int nIntervals = totalMinutes / intervalMinutes;
        int[] bucketed = new int[nIntervals];
        for (int i = 0; i < nIntervals; i++) {
            int sum = 0;
            int start = i * intervalMinutes;
            int end = Math.min(start + intervalMinutes, totalMinutes);
            for (int m = start; m < end; m++) sum += minuteArr[m];
            bucketed[i] = sum;
        }
        return bucketed;
    }


    public int getTotalMinutes() {
        return totalMinutes;
    }
}
