package com.canids.model;

/**
 * IEEE Paper: Node Annotation with Statistical Attributes
 * Stores statistical attributes (mean mu, std-dev sigma, sample count)
 * derived from time-series payload signals of a CAN ID node.
 */
public class SignalStats {
    private final String signalName;
    private double sum = 0.0;
    private double sumSq = 0.0;
    private int count = 0;
    private double min = Double.MAX_VALUE;
    private double max = -Double.MAX_VALUE;
    private double mean = 0.0;
    private double stdDev = 0.0;

    public SignalStats(String signalName) {
        this.signalName = signalName;
    }

    public synchronized void addSample(double value) {
        count++;
        sum += value;
        sumSq += value * value;
        if (value < min) min = value;
        if (value > max) max = value;

        mean = sum / count;
        if (count > 1) {
            double variance = (sumSq - (sum * sum) / count) / (count - 1);
            stdDev = Math.sqrt(Math.max(0.0, variance));
        } else {
            stdDev = 0.0;
        }
    }

    public String getSignalName() {
        return signalName;
    }

    public double getMean() {
        return mean;
    }

    public double getStdDev() {
        return stdDev;
    }

    public int getCount() {
        return count;
    }

    public double getMin() {
        return count == 0 ? 0 : min;
    }

    public double getMax() {
        return count == 0 ? 0 : max;
    }

    /**
     * Calculates the Z-score (number of standard deviations away from the mean).
     * Used for statistical anomaly detection as described in Section IV-C/E of the IEEE paper.
     */
    public double calculateZScore(double value) {
        if (count < 3 || stdDev == 0.0) {
            // Insufficient variance baseline or too few samples
            return 0.0;
        }
        return Math.abs(value - mean) / stdDev;
    }

    @Override
    public String toString() {
        return String.format("%s [n=%d, mean=%.2f, std=%.2f, min=%.2f, max=%.2f]",
                signalName, count, mean, stdDev, min, max);
    }
}
