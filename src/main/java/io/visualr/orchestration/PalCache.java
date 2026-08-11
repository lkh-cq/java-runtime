package io.visualr.orchestration;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline result cache — Java/JVM cache management (DEVELOPMENT_PLAN
 * §8 role). Keys are the exact task identity: kernel name + canonical
 * PAL format string. Identical tasks never recompute.
 *
 * <p>Simple bounded LRU (insertion-order map, evicts eldest at capacity).
 * A later slice may add TTL/disk persistence.</p>
 */
public final class PalCache {

    private final int capacity;
    private final LinkedHashMap<String, PalState> map;

    /** Create a cache holding at most {@code capacity} entries. */
    public PalCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(capacity, 0.75f, true);
    }

    /** Canonical cache key for a task. */
    public static String key(PalState pal, String kernelName) {
        return (kernelName == null ? "identity" : kernelName) + "\n" + PalCodec.format(pal);
    }

    /** Look up a cached result; empty when absent. */
    public synchronized Optional<PalState> get(PalState pal, String kernelName) {
        return Optional.ofNullable(map.get(key(pal, kernelName)));
    }

    /** Store a result under its task key (evicts eldest when at capacity). */
    public synchronized void put(PalState pal, String kernelName, PalState result) {
        map.put(key(pal, kernelName), result);
        while (map.size() > capacity) {
            Map.Entry<String, PalState> eldest = map.entrySet().iterator().next();
            map.remove(eldest.getKey());
        }
    }

    public synchronized int size() { return map.size(); }

    public synchronized void clear() { map.clear(); }
}
