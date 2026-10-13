package com.canids.research.road.data;

import java.util.Map;

public record RoadRecord(double timeSeconds, String canId, int label, Map<String, Double> signals) {
    public RoadRecord {
        signals = Map.copyOf(signals);
    }
}