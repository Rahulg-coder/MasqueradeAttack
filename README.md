# CAN Masquerade Attack Detection

One Java 21 / Maven project with two demonstration tracks:

1. **Live-like CAN simulator:** simulated ECU clients send CAN-like messages over TCP to a gateway. A rule IDS checks identity, message patterns, graph transitions, and signal Z-scores; suspicious clients can be blocked. The JavaFX dashboard displays activity.
2. **ROAD graph-ML experiment:** an offline Java implementation of the paper-aligned pipeline using ROAD signal_extractions CSVs. It builds sliding-window Message Sequence Graphs, calculates per-CAN-ID signal statistics, learns Node2Vec embeddings, applies SMOTE to training folds, and classifies windows with a Java Random Forest.

These are two run modes in one project. The offline ROAD classifier is not wired into the live TCP gateway, and neither track connects to physical CAN hardware.

## Open the project

Open this folder in IntelliJ IDEA and set Project SDK to Java 21. Maven resolves JavaFX and JGraphT from pom.xml.

## Project map

    src/main/java/com/canids/
      Main.java                         socket gateway demo entry point
      ECUTest.java                      normal / attacker client demo
      server/                           gateway and rule-based IDS
      graph/                            JGraphT message-sequence graph and node statistics
      model/                            CAN messages, ECU identities, signal statistics
      ecu/                              simulated ECU traffic
      attack/                           attack clients and scenarios
      mitigation/                       blocklist and client isolation
      logging/                          normal and attack CSV logging
      ui/                               JavaFX dashboard
      research/road/
        RoadExperimentMain.java         ROAD experiment entry point
        data/                            translated ROAD CSV parser and records
        feature/                         windows and per-CAN-ID signal statistics
        graph/                           weighted MSG and Node2Vec implementation
        ml/                              SMOTE and Java Random Forest
    docs/
      PROJECT_WALKTHROUGH.md             presentation order and algorithm map
      ROAD_PIPELINE.md                   parameters, data labels, and evaluation notes
      IEEE_ALGORITHM_MAPPING.md          line-by-line mapping of paper algorithms to Java
    logs/                                runtime CSV logs (generated locally; Git-ignored)
    road/road/signal_extractions/        local ROAD CSVs (download separately; Git-ignored)
    road-results.csv                     latest ROAD five-fold results

The ROAD dataset is not bundled in this repository. Obtain and prepare the ROAD signal_extractions CSVs, then place them under road/road/signal_extractions/. This Java pipeline does not decode raw candump logs or DBC files.

## Run the live-like simulator

In IntelliJ, run com.canids.Main to start the gateway. Run com.canids.ECUTest in a second configuration. Select normal mode to send valid traffic or attacker mode to exercise identity/masquerade detection. The normal and attack event CSVs are written to logs/.

To open the JavaFX dashboard, run com.canids.ui.DashboardUI.

## Run the ROAD experiment

Create an IntelliJ Application configuration:

- Main class: com.canids.research.road.RoadExperimentMain
- Program arguments: road\road\signal_extractions road-results.csv
- VM options: -Xmx8g

Optional window/stride arguments follow the result filename. Defaults are **6.0 seconds** and **3.2 seconds**, matching the paper's described time-window setting.

The runner prints progress for each capture and fold metrics, then writes road-results.csv. See docs/ROAD_PIPELINE.md for the algorithm and result interpretation.

## Implemented algorithms

The simulator implements identity validation, allowed message-pattern matching, directed transition checks, signal mean/std-dev tracking with an absolute Z-score threshold of 3, client blocking, and CSV logging.

The ROAD runner implements the reported Random Forest path in Java: ROAD CSV reading, duplicate-capture filtering, sliding windows, weighted CAN-ID graphs, per-node signal mean/std-dev features with linear interpolation, Node2Vec walks and skip-gram embeddings, graph/signal feature fusion, training-fold-only SMOTE, a custom Java Random Forest, and five-fold AUC-ROC/accuracy/inference-time reporting.

This is a **paper-aligned implementation, not an exact reproduction**. The paper does not specify all skip-gram settings; this code documents its choices. The paper also compares XGBoost but reports its Random Forest results; this Java project implements the reported Random Forest path, not the optional XGBoost comparison. Evaluation is stratified by windows, so overlapping neighboring windows can cross folds and make scores optimistic.

## Research novelty and project contribution

### Research novelty in the IEEE paper

Marfo et al. [1] propose a hybrid CAN masquerade detector that combines directed, weighted Message Sequence Graphs (MSGs), per-CAN-ID statistics from decoded signals (including mean and standard deviation), Node2Vec graph embeddings, feature fusion, and a Random Forest classifier. They evaluate the combined representation against graph-only and other feature settings on ROAD and report statistically significant gains over the graph-only baseline.

That detection method is the contribution of the cited paper. This repository does not claim the same method as a new algorithm invented by this project.

### Contribution of this repository

This project provides a software implementation and demonstration:

- A modular Java 21/Maven implementation of the combined graph-and-signal ROAD pipeline, including ROAD CSV ingestion, sliding-window MSG construction, per-CAN-ID signal statistics, Node2Vec, feature fusion, training-fold SMOTE, and a Java Random Forest.
- A five-fold window-level experiment runner that reports AUC-ROC, accuracy, and inference time per window.
- A separate live-like TCP ECU and attack simulator with a rule-based IDS and JavaFX dashboard. This is a demonstration track, separate from the offline ROAD classifier and the paper's evaluation.

The contribution is an implementation and dataset pipeline, not a new detection algorithm. The ROAD runner uses pretranslated signal-extraction CSVs and one configured window/stride pair by default. It does not reproduce the paper's full ablations, parameter sweeps, XGBoost comparison, or statistical significance tests. Since folds are assigned by window, overlapping neighboring windows may cross folds; treat the reported metrics as this project's results rather than a direct reproduction of the paper's results.

### Suggested manuscript title

**Java Implementation and ROAD Dataset Evaluation of Graph-and-Signal Feature Fusion for CAN Masquerade Detection**

### Manuscript-ready contribution statement

Building on the method proposed by Marfo et al. [1], this work presents a modular Java implementation of graph-and-signal feature fusion for CAN masquerade detection and applies it to ROAD signal-extraction data. The implementation includes sliding-window message sequence graphs, per-CAN-ID signal statistics, Node2Vec embeddings, training-fold-only SMOTE, and a Java Random Forest, with five-fold window-level evaluation using AUC-ROC, accuracy, and inference time per window. The contribution is an implementation and dataset pipeline; it does not claim a new detection algorithm. The evaluation uses one configured window/stride setting and does not reproduce all baselines, parameter sweeps, or statistical tests in the original paper.

### Reference

[1] W. Marfo, P. Moriano, D. K. Tosh, and S. V. Moore, "Detecting Masquerade Attacks in Controller Area Networks Using Graph Machine Learning," *IEEE Transactions on Information Forensics and Security*, vol. 20, 2025. [https://doi.org/10.1109/TIFS.2025.3636019](https://doi.org/10.1109/TIFS.2025.3636019).
