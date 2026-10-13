package com.canids.research.road;

import com.canids.research.road.data.RoadCapture;
import com.canids.research.road.data.RoadDatasetReader;
import com.canids.research.road.feature.SlidingWindowFeatureExtractor;
import com.canids.research.road.feature.WindowSample;
import com.canids.research.road.graph.Node2VecEncoder;
import com.canids.research.road.ml.RandomForestClassifier;
import com.canids.research.road.ml.Smote;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Runs the paper's end-to-end ROAD detection stages with this project's CSV adapter and evaluation.
 *
 * <p>Flow: read labeled captures -> build windowed MSGs and node statistics (paper
 * Algorithms 2 and 3) -> fit Node2Vec on training-fold graphs -> fuse graph and
 * signal features -> apply training-fold SMOTE -> train Random Forest -> score
 * held-out windows.
 *
 * <p>This runner uses pretranslated signal-extraction CSVs; it does not decode raw
 * CAN logs with DBC files or generate attacks. Its shared training-fold Node2Vec
 * encoder is an implementation adaptation to the paper's per-window embedding flow.
 */
public final class RoadExperimentMain {
    private static final int FOLDS = 5;
    private static final double DEFAULT_WINDOW_SECONDS = 6.0;
    private static final double DEFAULT_STRIDE_SECONDS = 3.2;

    private RoadExperimentMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args.length > 1 ? args[1] : "road-results.csv");
        double windowSeconds = args.length > 2
                ? parsePositive(args[2], "window seconds")
                : DEFAULT_WINDOW_SECONDS;
        double strideSeconds = args.length > 3
                ? parsePositive(args[3], "stride seconds")
                : DEFAULT_STRIDE_SECONDS;

        List<Path> csvFiles = RoadDatasetReader.csvFiles(input);
        if (csvFiles.isEmpty()) {
            throw new IOException("No ROAD CSV files found under " + input);
        }
        List<String> signalColumns = RoadDatasetReader.signalColumns(csvFiles);

        List<WindowSample> samples = new ArrayList<>();
        SlidingWindowFeatureExtractor extractor =
                new SlidingWindowFeatureExtractor(windowSeconds, strideSeconds);
        int captureCount = 0;

        // Stream captures one at a time to avoid retaining all raw ROAD rows in memory.
        for (Path csv : csvFiles) {
            var captureOption = RoadDatasetReader.readCapture(input, csv);
            if (captureOption.isEmpty()) {
                continue;
            }
            RoadCapture capture = captureOption.get();
            extractor.extract(capture, signalColumns, samples);
            captureCount++;
            System.out.printf(
                    "Processed %s; captures=%d windows=%d%n",
                    capture.name(), captureCount, samples.size());
        }

        int attackWindows = (int) samples.stream().filter(sample -> sample.label() == 1).count();
        int normalWindows = samples.size() - attackWindows;
        if (attackWindows < FOLDS || normalWindows < FOLDS) {
            throw new IllegalStateException(
                    "Need at least " + FOLDS + " windows per class; normal="
                            + normalWindows + ", attack=" + attackWindows);
        }

        List<List<Integer>> folds = stratifiedWindowFolds(samples, FOLDS, 42);
        List<String> resultLines = new ArrayList<>();
        resultLines.add("fold,windows,auc_roc,accuracy,ttw_ms_per_window");

        for (int fold = 0; fold < FOLDS; fold++) {
            List<WindowSample> train = new ArrayList<>();
            List<WindowSample> test = new ArrayList<>();
            boolean[] heldOut = new boolean[samples.size()];
            for (int index : folds.get(fold)) {
                heldOut[index] = true;
            }
            for (int index = 0; index < samples.size(); index++) {
                (heldOut[index] ? test : train).add(samples.get(index));
            }

            // Paper Section IV-D settings: 64 dimensions, 100 walks/node, length 15, p=1.5, q=0.5.
            // This encoder shares vectors learned from training-fold graphs; skip-gram details are implementation choices.
            Node2VecEncoder encoder = new Node2VecEncoder(64, 100, 15, 1.5, 0.5, 1, 42 + fold);
            encoder.fit(train);

            double[][] features = new double[train.size()][];
            int[] labels = new int[train.size()];
            for (int i = 0; i < train.size(); i++) {
                WindowSample sample = train.get(i);
                features[i] = join(encoder.encode(sample.graph()), sample.pooledSignalStatistics());
                labels[i] = sample.label();
            }

            // Paper Section IV-E: balance only the training fold to the stated 1:10 minority/majority ratio.
            Smote.Dataset balanced = Smote.oversample(features, labels, 0.1, 100 + fold);
            RandomForestClassifier classifier =
                    new RandomForestClassifier(100, 20, 6, 2, 200 + fold);
            classifier.fit(balanced.features(), balanced.labels());

            List<Integer> actual = new ArrayList<>();
            List<Double> scores = new ArrayList<>();
            long inferenceNanos = 0;
            int correct = 0;
            for (WindowSample sample : test) {
                long startNanos = System.nanoTime();
                double score = classifier.predictProbability(
                        join(encoder.encode(sample.graph()), sample.pooledSignalStatistics()));
                inferenceNanos += System.nanoTime() - startNanos;
                actual.add(sample.label());
                scores.add(score);
                if ((score >= 0.5 ? 1 : 0) == sample.label()) {
                    correct++;
                }
            }

            double auc = auc(actual, scores);
            double accuracy = (double) correct / test.size();
            double timePerWindowMs = inferenceNanos / 1_000_000.0 / test.size();
            resultLines.add(String.format(
                    Locale.ROOT, "%d,%d,%.6f,%.6f,%.6f",
                    fold + 1, test.size(), auc, accuracy, timePerWindowMs));
            System.out.printf(
                    Locale.ROOT,
                    "Fold %d AUC %.4f accuracy %.4f TTW %.4f ms/window%n",
                    fold + 1, auc, accuracy, timePerWindowMs);
        }

        Path absoluteOutput = output.toAbsolutePath();
        if (absoluteOutput.getParent() != null) {
            Files.createDirectories(absoluteOutput.getParent());
        }
        Files.write(absoluteOutput, resultLines);
        System.out.printf(
                "Captures=%d windows=%d normal=%d attack=%d results=%s%n",
                captureCount, samples.size(), normalWindows, attackWindows, absoluteOutput);
        System.out.printf(
                Locale.ROOT, "Window=%.1fs stride=%.1fs uniqueSignalColumns=%d%n",
                windowSeconds, strideSeconds, signalColumns.size());
    }

    private static void printUsage() {
        System.out.println("""
                Usage: RoadExperimentMain <signal_extractions folder or CSV> [results.csv]
                       [windowSeconds] [strideSeconds]

                Defaults: windowSeconds=6.0, strideSeconds=3.2
                IntelliJ VM option for the full ROAD dataset: -Xmx8g
                """);
    }

    private static double parsePositive(String value, String name) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed) || parsed <= 0) {
            throw new IllegalArgumentException(name + " must be a finite positive number");
        }
        return parsed;
    }

    /** Stratifies deterministic folds by window label; overlapping windows may cross fold boundaries. */
    private static List<List<Integer>> stratifiedWindowFolds(
            List<WindowSample> samples, int foldCount, long seed) {
        List<Integer> attacks = new ArrayList<>();
        List<Integer> normal = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            (samples.get(i).label() == 1 ? attacks : normal).add(i);
        }

        Random random = new Random(seed);
        Collections.shuffle(attacks, random);
        Collections.shuffle(normal, random);

        List<List<Integer>> folds = new ArrayList<>();
        for (int i = 0; i < foldCount; i++) {
            folds.add(new ArrayList<>());
        }
        for (int i = 0; i < attacks.size(); i++) {
            folds.get(i % foldCount).add(attacks.get(i));
        }
        for (int i = 0; i < normal.size(); i++) {
            folds.get(i % foldCount).add(normal.get(i));
        }
        return folds;
    }

    /** Paper Algorithm 1 feature fusion: concatenate pooled graph embeddings and signal statistics. */
    private static double[] join(double[] graphEmbedding, double[] signalStatistics) {
        double[] features = new double[graphEmbedding.length + signalStatistics.length];
        System.arraycopy(graphEmbedding, 0, features, 0, graphEmbedding.length);
        System.arraycopy(signalStatistics, 0, features, graphEmbedding.length, signalStatistics.length);
        return features;
    }

    /** Computes AUC-ROC from ranked scores, assigning average ranks to ties. */
    private static double auc(List<Integer> labels, List<Double> scores) {
        record RankedScore(double score, int label) {
        }

        List<RankedScore> ranked = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            ranked.add(new RankedScore(scores.get(i), labels.get(i)));
        }
        ranked.sort(Comparator.comparingDouble(RankedScore::score));

        int positives = (int) labels.stream().filter(label -> label == 1).count();
        int negatives = labels.size() - positives;
        if (positives == 0 || negatives == 0) {
            return Double.NaN;
        }

        double positiveRankSum = 0.0;
        for (int start = 0; start < ranked.size();) {
            int end = start + 1;
            while (end < ranked.size()
                    && Double.compare(ranked.get(start).score(), ranked.get(end).score()) == 0) {
                end++;
            }
            double averageRank = ((start + 1) + end) / 2.0;
            for (int i = start; i < end; i++) {
                if (ranked.get(i).label() == 1) {
                    positiveRankSum += averageRank;
                }
            }
            start = end;
        }
        return (positiveRankSum - positives * (positives + 1) / 2.0)
                / (positives * (double) negatives);
    }
}
