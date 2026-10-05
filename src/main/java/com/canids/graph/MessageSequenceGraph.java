package com.canids.graph;

import com.canids.model.SignalStats;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SRS 3.1 & IEEE Paper Section IV-A / IV-C:
 * Represents CAN traffic as a Message Sequence Graph (MSG) using JGraphT.
 * Nodes correspond to CAN Message IDs / ECUs.
 * Edges represent sequential relationships between consecutive messages.
 * Edge weights quantify observed communication sequence frequencies.
 * Nodes are annotated with statistical attributes (mean mu, std-dev sigma) of signals.
 */
public class MessageSequenceGraph {
    private final Graph<String, DefaultWeightedEdge> graph;
    private final Map<String, Map<String, SignalStats>> nodeSignalStats;
    private String lastObservedEcu = null;

    public MessageSequenceGraph() {
        this.graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
        this.nodeSignalStats = new ConcurrentHashMap<>();
    }

    /**
     * Registers an ECU / Node in the graph.
     */
    public synchronized void addEcuNode(String ecuId) {
        if (!graph.containsVertex(ecuId)) {
            graph.addVertex(ecuId);
            nodeSignalStats.putIfAbsent(ecuId, new ConcurrentHashMap<>());
        }
    }

    /**
     * Adds or updates a directed edge v_prev -> v_curr representing message sequence transition.
     */
    public synchronized void recordSequenceTransition(String sourceEcu, String targetEcu) {
        addEcuNode(sourceEcu);
        addEcuNode(targetEcu);

        DefaultWeightedEdge edge = graph.getEdge(sourceEcu, targetEcu);
        if (edge == null) {
            edge = graph.addEdge(sourceEcu, targetEcu);
            if (edge != null) {
                graph.setEdgeWeight(edge, 1.0);
            }
        } else {
            double currentWeight = graph.getEdgeWeight(edge);
            graph.setEdgeWeight(edge, currentWeight + 1.0);
        }
        lastObservedEcu = targetEcu;
    }

    /**
     * Records a numerical signal value for node annotation (IEEE Paper Section IV-C).
     */
    public synchronized void recordSignalValue(String ecuId, String signalName, double value) {
        addEcuNode(ecuId);
        Map<String, SignalStats> signals = nodeSignalStats.computeIfAbsent(ecuId, k -> new ConcurrentHashMap<>());
        SignalStats stats = signals.computeIfAbsent(signalName, k -> new SignalStats(signalName));
        stats.addSample(value);
    }

    /**
     * Checks if a transition v_source -> v_target exists in the learned MSG.
     */
    public synchronized boolean hasValidTransition(String sourceEcu, String targetEcu) {
        return graph.containsVertex(sourceEcu) &&
                graph.containsVertex(targetEcu) &&
                graph.containsEdge(sourceEcu, targetEcu);
    }

    /**
     * Returns edge weight (frequency) for transition v_source -> v_target.
     */
    public synchronized double getTransitionWeight(String sourceEcu, String targetEcu) {
        DefaultWeightedEdge edge = graph.getEdge(sourceEcu, targetEcu);
        return (edge != null) ? graph.getEdgeWeight(edge) : 0.0;
    }

    /**
     * Gets SignalStats for a specific ECU node and signal name.
     */
    public synchronized SignalStats getSignalStats(String ecuId, String signalName) {
        Map<String, SignalStats> signals = nodeSignalStats.get(ecuId);
        return (signals != null) ? signals.get(signalName) : null;
    }

    public synchronized Set<String> getNodes() {
        return Collections.unmodifiableSet(graph.vertexSet());
    }

    public synchronized Set<DefaultWeightedEdge> getEdges() {
        return Collections.unmodifiableSet(graph.edgeSet());
    }

    public synchronized String getEdgeSource(DefaultWeightedEdge edge) {
        return graph.getEdgeSource(edge);
    }

    public synchronized String getEdgeTarget(DefaultWeightedEdge edge) {
        return graph.getEdgeTarget(edge);
    }

    public synchronized String getLastObservedEcu() {
        return lastObservedEcu;
    }

    public synchronized String getGraphSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("MSG Topology: %d Nodes, %d Edges | ", graph.vertexSet().size(), graph.edgeSet().size()));
        sb.append("Nodes: ").append(graph.vertexSet());
        return sb.toString();
    }
}
