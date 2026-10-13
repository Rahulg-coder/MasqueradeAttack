# ROAD graph-ML experiment

## Algorithm

1. Read the ROAD translated Label, Time, ID, and Signal_*_of_ID CSV columns. The reader hashes CSV files and drops byte-identical duplicates, preferring a copy under attacks when duplicates exist.
2. Normalize timestamps within each capture and process one capture at a time to control memory.
3. Create complete 6-second windows with a 3.2-second stride by default. A window is attack-labeled if it contains at least one ROAD row labeled as attack.
4. In each window, create one node per CAN ID and a directed weighted edge for each consecutive CAN-ID pair. The edge weight counts that transition in the window.
5. For each CAN-ID node and signal column, linearly interpolate missing values within the window and calculate the mean and sample standard deviation. Mean-pool node statistics into a fixed-size window vector.
6. Fit Node2Vec on training-fold graphs only: 64 dimensions, 100 walks per node, walk length 15, p=1.5, q=0.5. Skip-gram settings are context radius 5, five negative samples, one epoch, and learning rate 0.025. The paper does not specify these latter values. Node vectors are mean-pooled per window and concatenated with pooled signal statistics.
7. Apply SMOTE only to the training fold, targeting a 1:10 minority-to-majority ratio.
8. Train the Java Random Forest: 100 trees, maximum depth 20, minimum split size 6, minimum leaf size 2.
9. Run deterministic, stratified five-fold evaluation at the window level. Report AUC-ROC, accuracy, and measured inference time per window.

## Code map

- data/RoadDatasetReader.java: signal-extraction CSV discovery, duplicate hashing, parsing, CAN ID/time/label normalization.
- feature/SlidingWindowFeatureExtractor.java: windows, directed weighted MSGs, per-ID interpolation/statistics, attack-window labels.
- feature/WindowSample.java: window label, graph, per-node attributes, and pooled graph-level signal vector.
- graph/WindowGraph.java: directed CAN-ID transition graph.
- graph/Node2VecEncoder.java: biased second-order walks, skip-gram negative sampling, and mean-pooled window embeddings.
- ml/Smote.java: training-only minority oversampling.
- ml/RandomForestClassifier.java: bootstrap trees with Gini-impurity splits and class probabilities.
- RoadExperimentMain.java: fold creation, model training, metrics, and CSV output.

## Run

Use an IntelliJ Application configuration:
- Main class: com.canids.research.road.RoadExperimentMain
- Program arguments: road\road\signal_extractions road-results.csv
- VM options: -Xmx8g

Optional arguments after the output path: windowSeconds strideSeconds. Example: road\road\signal_extractions road-results.csv 6.0 3.2.

The Java program requires translated ROAD signal_extractions CSVs. It does not parse raw candump .log files or decode DBC files itself. The ROAD dataset is external and is not bundled here. Obtain the dataset and prepare translated signal_extractions CSVs under road/road/signal_extractions/. The runner does not parse raw candump logs or decode DBC files.

## Interpretation and limits

- The current five-fold split assigns overlapping windows independently. Neighboring windows share traffic, so this is not capture-held-out generalization; metrics may be optimistic for unseen captures.
- TTW here times embedding lookup/encoding, feature fusion, and Random Forest prediction. It excludes graph construction and Node2Vec training.
- This is aggregate ROAD evaluation. Do not compare it directly with paper tables reporting attack-specific configurations.
- The reference paper also evaluated XGBoost and found similar results, but reported Random Forest results. This Java project implements the reported Random Forest classifier; the optional XGBoost comparison is not included.
- Describe this as paper-aligned rather than an exact reproduction. The paper does not fully specify skip-gram training settings, and this Java implementation documents its choices.

## Optional vehicular mobility demo

The Java socket ECU/gateway simulator is the relevant demo for in-vehicle CAN-like messages. SUMO and Veins model road mobility and V2X communication; they are optional and are not required for the ROAD CAN experiment.
