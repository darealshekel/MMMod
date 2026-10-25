package com.mmm.timer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-thread ledger of completed predictions awaiting vanilla reconciliation. */
final class ConfirmedBreaks<K, V> {
    private final Map<K, Entry<V>> pending = new LinkedHashMap<>();
    private Object world;

    void completed(Object world, K position, V value, long now) {
        expire(world, now);
        pending.put(position, new Entry<>(value, now));
        while (pending.size() > 2048) pending.remove(pending.keySet().iterator().next());
    }

    V confirm(Object world, K position, boolean accepted, long now) {
        expire(world, now);
        Entry<V> entry = pending.remove(position);
        return accepted && entry != null ? entry.value() : null;
    }

    void expire(Object currentWorld, long now) {
        if (world != currentWorld) {
            pending.clear();
            world = currentWorld;
        }
        pending.values().removeIf(entry -> now - entry.completedAt() > 30000L);
    }

    void clear() { pending.clear(); world = null; }
    int size() { return pending.size(); }
    private record Entry<V>(V value, long completedAt) {}
}
