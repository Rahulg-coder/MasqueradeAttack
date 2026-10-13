package com.canids.research.road.graph;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Paper Algorithm 2 representation: a directed, weighted CAN-ID transition graph for one window. */
public final class WindowGraph {
    private final Map<String, Map<String, Double>> outgoing = new TreeMap<>();

    public void addNode(String id) {
        outgoing.computeIfAbsent(id, ignored -> new TreeMap<>());
    }

    public void addTransition(String source, String target) {
        addNode(source);
        addNode(target);
        outgoing.get(source).merge(target, 1.0, Double::sum);
    }

    public Set<String> nodes() {
        return Collections.unmodifiableSet(outgoing.keySet());
    }

    public Map<String, Double> outgoing(String id) {
        return outgoing.getOrDefault(id, Map.of());
    }

    public boolean hasEdge(String from, String to) {
        return outgoing(from).containsKey(to);
    }

    public boolean hasEdges() {
        return outgoing.values().stream().anyMatch(edges -> !edges.isEmpty());
    }
}