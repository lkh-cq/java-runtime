package io.visualr.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.visualr.runtime.PalState;

/**
 * PalCache: bounded LRU keyed by task identity (kernel + canonical PAL).
 */
class PalCacheTest {

    private static PalState s4() {
        return PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static PalState rotateResult() {
        return PalState.of(List.of("B", "C", "D", "A"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    @Test
    void putGetHit() {
        PalCache cache = new PalCache(8);
        PalState p = s4();
        assertTrue(cache.get(p, "identity").isEmpty());

        cache.put(p, "identity", p);
        Optional<PalState> hit = cache.get(p, "identity");
        assertTrue(hit.isPresent());
        assertEquals(p, hit.get());
    }

    @Test
    void kernelDifferentiatesKeys() {
        PalCache cache = new PalCache(8);
        PalState p = s4();
        PalState rotated = rotateResult();
        cache.put(p, "identity", p);
        cache.put(p, "rotate", rotated);

        assertEquals(p, cache.get(p, "identity").orElseThrow());
        assertEquals(rotated, cache.get(p, "rotate").orElseThrow());
    }

    @Test
    void lruEvictsEldest() {
        PalCache cache = new PalCache(2);
        PalState a = s4();
        PalState b = PalState.of(List.of("X"), "e", PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        PalState c = PalState.of(List.of("Y"), "e", PalState.DEFAULT_MAPPING_PACK_ID, Map.of());

        cache.put(a, "identity", a);
        cache.put(b, "identity", b);
        cache.get(a, "identity");            // touch a -> a becomes most-recent
        cache.put(c, "identity", c);         // evicts b (least-recent)

        assertTrue(cache.get(a, "identity").isPresent(), "a kept (touched)");
        assertFalse(cache.get(b, "identity").isPresent(), "b evicted (eldest)");
        assertTrue(cache.get(c, "identity").isPresent(), "c present");
        assertEquals(2, cache.size());
    }

    @Test
    void clearEmpties() {
        PalCache cache = new PalCache(8);
        PalState p = s4();
        cache.put(p, "identity", p);
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(cache.get(p, "identity").isEmpty());
    }
}
