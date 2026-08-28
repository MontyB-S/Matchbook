package com.monty.matchbook.matching;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ProcessedEvents {

    private final Set<UUID> seen;

    ProcessedEvents(@Value("${matchbook.matching.dedup-capacity}") int capacity) {
        this.seen = Collections.synchronizedSet(Collections.newSetFromMap(new LinkedHashMap<>(capacity, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                return size() > capacity;
            }
        }));
    }

    boolean isNew(UUID eventId) {
        return seen.add(eventId);
    }

    int size() {
        return seen.size();
    }
}
