# Presentation walkthrough

## Suggested order

1. **Show the live-like simulator.** Start com.canids.Main, then run com.canids.ECUTest in another IntelliJ configuration. Demonstrate one accepted ECU message and one impersonation/attack message. Point out the gateway decision, block action, CSV log, and JavaFX dashboard.
2. **Explain the simulator's rule IDS.** It checks actual TCP client identity against the claimed ECU, allowed message keys, the last observed CAN-ID transition, and numeric signal values using an absolute Z-score threshold of 3.
3. **Open the ROAD runner.** Show RoadExperimentMain, then follow the reader, window extractor, Node2Vec encoder, SMOTE, and Random Forest classes using the source map in README.md.
4. **Run the ROAD experiment.** Use arguments road\road\signal_extractions road-results.csv and VM option -Xmx8g. The output CSV contains the five fold metrics.
5. **State the scope accurately.** Both tracks are Java code in one Maven project. ROAD training/evaluation is offline; it is not connected to the simulator's live gateway or physical CAN hardware.

## Algorithm in one sentence

ROAD signal-extraction rows are grouped into time windows; each window becomes a weighted directed graph of consecutive CAN IDs, with per-ID signal means and standard deviations. Node2Vec graph embeddings and pooled signal statistics are fused, training data is balanced with SMOTE, and a Random Forest classifies each window.

## Questions to expect

- **Why graph features?** CAN-ID transition graphs encode message ordering and repeated transition frequencies; signal statistics capture changes in decoded signal behavior.
- **What does Node2Vec add?** Biased random walks generate node contexts, and skip-gram learns a compact vector for each CAN ID. Mean pooling gives one embedding for each window graph.
- **Why SMOTE?** Attack windows are the minority class. SMOTE synthesizes minority examples from the training fold only.
- **Why Random Forest?** It is the classifier whose results the paper reports. The Java project implements the forest directly so the algorithm is visible in the source.
- **Is this exactly the paper?** It follows the reported pipeline and settings where specified. The paper does not specify every skip-gram parameter; the Java implementation states its choices, and this aggregate experiment uses window-level stratified folds rather than capture-held-out evaluation.
- **Do we need SUMO?** No. SUMO models road traffic and mobility; the project demonstrates in-vehicle CAN-like traffic with simulated ECUs and a Java gateway.
- **Can it connect to a vehicle?** No. The simulator uses localhost TCP and is not connected to real CAN hardware.
