package com.canids.research.road.feature;

import com.canids.research.road.graph.WindowGraph;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** One labeled window containing its MSG and per-CAN-ID signal annotations.
 *
 * <p>Node statistics are mean-pooled across the window's CAN IDs to make a
 * fixed-width signal vector for feature fusion.
 */
public record WindowSample(
        String captureName,
        double startSeconds,
        int label,
        WindowGraph graph,
        Map<String, double[]> nodeSignalStatistics) {

    public WindowSample {
        if (captureName == null || graph == null || nodeSignalStatistics == null) {
            throw new IllegalArgumentException("Window fields cannot be null");
        }
        if (label != 0 && label != 1) {
            throw new IllegalArgumentException("Label must be 0 or 1");
        }
        Map<String, double[]> copy = new TreeMap<>();
        nodeSignalStatistics.forEach((canId, statistics) -> {
            if (canId == null || statistics == null) {
                throw new IllegalArgumentException("Node annotations cannot contain nulls");
            }
            copy.put(canId, statistics.clone());
        });
        nodeSignalStatistics = Collections.unmodifiableMap(copy);
    }

    /** Returns deep copies so callers cannot mutate stored node annotations. */
    @Override
    public Map<String, double[]> nodeSignalStatistics() {
        Map<String, double[]> copy = new TreeMap<>();
        nodeSignalStatistics.forEach((canId, statistics) -> copy.put(canId, statistics.clone()));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Mean-pools each node's [signal mean, signal standard deviation] values
     * into a fixed-size vector for window-level feature fusion.
     */
    public double[] pooledSignalStatistics() {
        if (nodeSignalStatistics.isEmpty()) {
            return new double[0];
        }
        int width = nodeSignalStatistics.values().iterator().next().length;
        double[] pooled = new double[width];
        for (double[] nodeStatistics : nodeSignalStatistics.values()) {
            if (nodeStatistics.length != width) {
                throw new IllegalStateException("All node annotation vectors must have the same width");
            }
            for (int i = 0; i < width; i++) {
                pooled[i] += nodeStatistics[i];
            }
        }
        for (int i = 0; i < width; i++) {
            pooled[i] /= nodeSignalStatistics.size();
        }
        return pooled;
    }
}
