package com.canids.research.road.data;

import java.util.Collections;
import java.util.List;

public record RoadCapture(String name, List<RoadRecord> records) {
    public RoadCapture {
        // The reader transfers ownership of this list; wrap it without copying all rows.
        records = Collections.unmodifiableList(records);
    }
}