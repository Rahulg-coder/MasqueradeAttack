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
