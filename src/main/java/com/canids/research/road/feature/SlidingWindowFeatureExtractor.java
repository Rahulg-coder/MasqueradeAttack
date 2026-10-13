package com.canids.research.road.feature;

import com.canids.research.road.data.RoadCapture;
import com.canids.research.road.data.RoadRecord;
import com.canids.research.road.graph.WindowGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implements paper Algorithm 2 (MSG construction) and Algorithm 3 (node annotation).
 *
 * <p>Algorithm 2: slide complete windows -> add CAN IDs as nodes -> count each
 * adjacent CAN-ID pair as a directed edge weight.
 *
 * <p>Algorithm 3: group rows by CAN ID -> interpolate each signal's missing values
 * within that node's window rows -> calculate mean and sample standard deviation
 * -> attach the statistics to the node. A window is positive if any row is labeled
 * as an attack.
 */
public final class SlidingWindowFeatureExtractor {
    private final double windowSeconds;
    private final double strideSeconds;

    public SlidingWindowFeatureExtractor(double windowSeconds, double strideSeconds) {
        if (!Double.isFinite(windowSeconds) || !Double.isFinite(strideSeconds)
                || windowSeconds <= 0 || strideSeconds <= 0) {
            throw new IllegalArgumentException("Window and stride must be finite positive values");
        }
        this.windowSeconds = windowSeconds;
        this.strideSeconds = strideSeconds;
    }

    public List<WindowSample> extract(List<RoadCapture> captures, List<String> signalColumns) {
        List<WindowSample> samples = new ArrayList<>();
        for (RoadCapture capture : captures) {
            extract(capture, signalColumns, samples);
        }
        return List.copyOf(samples);
    }

    /** Builds complete windows from half-open [start, end) ranges for one capture. */
    public void extract(RoadCapture capture, List<String> signalColumns, List<WindowSample> destination) {
        List<RoadRecord> rows = capture.records().stream()
                .sorted(Comparator.comparingDouble(RoadRecord::timeSeconds))
                .toList();
        if (rows.isEmpty()) {
            return;
        }
        double duration = rows.getLast().timeSeconds();
        if (duration + 1e-9 < windowSeconds) {
            return;
        }

        long windowIndex = 0;
        double start = 0;
        while (start + windowSeconds <= duration + 1e-9) {
            double end = start + windowSeconds;
            int left = lowerBound(rows, start);
            int right = lowerBound(rows, end);
            if (right > left) {
                addWindow(capture, start, rows.subList(left, right), signalColumns, destination);
            }
            windowIndex++;
            start = windowIndex * strideSeconds;
        }
    }

    private static int lowerBound(List<RoadRecord> rows, double time) {
        int low = 0;
        int high = rows.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (rows.get(middle).timeSeconds() < time) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static void addWindow(
            RoadCapture capture,
            double start,
            List<RoadRecord> rows,
            List<String> signalColumns,
            List<WindowSample> destination) {

        WindowGraph graph = new WindowGraph();
        Map<String, List<RoadRecord>> rowsByCanId = new TreeMap<>();
        int attackRows = 0;
        String previousCanId = null;
        for (RoadRecord row : rows) {
            String canId = row.canId();
            graph.addNode(canId);
            if (previousCanId != null) {
                graph.addTransition(previousCanId, canId);
            }
            previousCanId = canId;
            rowsByCanId.computeIfAbsent(canId, ignored -> new ArrayList<>()).add(row);
            if (row.label() == 1) {
                attackRows++;
            }
        }

        Map<String, double[]> annotations = new TreeMap<>();
        rowsByCanId.forEach((canId, canRows) ->
                annotations.put(canId, nodeSignalStatistics(canRows, signalColumns)));

        // A ROAD window is positive when it contains at least one attack-labeled row.
        destination.add(new WindowSample(
                capture.name(), start, attackRows > 0 ? 1 : 0, graph, annotations));
    }

    /** Paper Algorithm 3: interpolate a node's signal samples and calculate mean and sample standard deviation. */
    private static double[] nodeSignalStatistics(List<RoadRecord> rows, List<String> signalColumns) {
        double[] result = new double[signalColumns.size() * 2];
        for (int signalIndex = 0; signalIndex < signalColumns.size(); signalIndex++) {
            double[] values = new double[rows.size()];
            Arrays.fill(values, Double.NaN);
            String signal = signalColumns.get(signalIndex);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Double value = rows.get(rowIndex).signals().get(signal);
                if (value != null && Double.isFinite(value)) {
                    values[rowIndex] = value;
                }
            }
            interpolate(values);
            double mean = Arrays.stream(values).average().orElse(0.0);
            double squaredDifferenceSum = 0.0;
            for (double value : values) {
                double difference = value - mean;
                squaredDifferenceSum += difference * difference;
            }
            result[signalIndex * 2] = mean;
            result[signalIndex * 2 + 1] = values.length > 1
                    ? Math.sqrt(squaredDifferenceSum / (values.length - 1))
                    : 0.0;
        }
        return result;
    }

    private static void interpolate(double[] values) {
        int firstObserved = -1;
        for (int i = 0; i < values.length; i++) {
            if (Double.isFinite(values[i])) {
                firstObserved = i;
                break;
            }
        }
        if (firstObserved < 0) {
            Arrays.fill(values, 0.0);
            return;
        }

        Arrays.fill(values, 0, firstObserved, values[firstObserved]);
        int previousObserved = firstObserved;
        for (int i = firstObserved + 1; i < values.length; i++) {
            if (!Double.isFinite(values[i])) {
                continue;
            }
            double startValue = values[previousObserved];
            double endValue = values[i];
            int gap = i - previousObserved;
            for (int step = 1; step < gap; step++) {
                values[previousObserved + step] =
                        startValue + (endValue - startValue) * step / gap;
            }
            previousObserved = i;
        }
        Arrays.fill(values, previousObserved + 1, values.length, values[previousObserved]);
    }
}
