package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Lane kernel registry semantics — faithful port of R orbit_operators.R
 * built-in kernels (identity/complement/mirror/rotate/gamma) plus the
 * spec resolver (lane_kernels).
 */
class LaneKernelRegistryTest {

    @Test
    void registryListsSortedNames() {
        List<String> names = LaneKernelRegistry.list();
        assertEquals(List.of("complement", "gamma", "identity", "mirror", "rotate"), names);
    }

    @Test
    void unknownKernelFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> LaneKernelRegistry.get("nonexistent"));
    }

    @Test
    void identityKernel() {
        LaneResult r = LaneKernelRegistry.get("identity")
                .apply(new String[] {"A", "A"}, "idle", null);
        assertArrayEquals(new String[] {"A", "A"}, r.endpoints());
        assertEquals("identity", r.action());
    }

    @Test
    void complementKernel() {
        LaneResult r = LaneKernelRegistry.get("complement")
                .apply(new String[] {"A", "B"}, "idle", null);
        // A<->D, B<->C
        assertArrayEquals(new String[] {"D", "C"}, r.endpoints());
        assertEquals("complement", r.action());
        // involution C^2 = I
        LaneResult r2 = LaneKernelRegistry.get("complement")
                .apply(r.endpoints(), "idle", null);
        assertArrayEquals(new String[] {"A", "B"}, r2.endpoints());
        // null endpoints pass through; unknown tokens unchanged
        LaneResult r3 = LaneKernelRegistry.get("complement")
                .apply(new String[] {null, "X"}, "idle", null);
        assertArrayEquals(new String[] {null, "X"}, r3.endpoints());
    }

    @Test
    void complementHonorsPackTable() {
        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("A", "Z");
        custom.put("Z", "A");
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("complement_table", custom);
        LaneResult r = LaneKernelRegistry.get("complement")
                .apply(new String[] {"A"}, "idle", pack);
        assertArrayEquals(new String[] {"Z"}, r.endpoints());
    }

    @Test
    void mirrorKernel() {
        LaneResult r = LaneKernelRegistry.get("mirror")
                .apply(new String[] {"A", "D"}, "idle", null);
        assertArrayEquals(new String[] {"D", "A"}, r.endpoints());
        assertEquals("mirror", r.action());
        // singularity lane (length 1): center mirrors itself
        LaneResult s = LaneKernelRegistry.get("mirror")
                .apply(new String[] {"e"}, "idle", null);
        assertArrayEquals(new String[] {"e"}, s.endpoints());
    }

    @Test
    void rotateKernel() {
        LaneResult r = LaneKernelRegistry.get("rotate")
                .apply(new String[] {"A", "D"}, "idle", null);
        // A->B, D->A
        assertArrayEquals(new String[] {"B", "A"}, r.endpoints());
        assertEquals("rotate", r.action());
        LaneResult r2 = LaneKernelRegistry.get("rotate")
                .apply(new String[] {"X", null}, "idle", null);
        assertArrayEquals(new String[] {"X", null}, r2.endpoints());
    }

    @Test
    void gammaKernel() {
        LaneResult r = LaneKernelRegistry.get("gamma")
                .apply(new String[] {"A", "D"}, "idle", null);
        // A->B, D->e (clamped)
        assertArrayEquals(new String[] {"B", "e"}, r.endpoints());
        assertEquals("gamma", r.action());
        LaneResult r2 = LaneKernelRegistry.get("gamma")
                .apply(new String[] {"e"}, "idle", null);
        assertArrayEquals(new String[] {"e"}, r2.endpoints()); // clamped at e
    }

    @Test
    void specStringAppliesToAllLanes() {
        Map<String, LaneKernel> kernels = LaneKernelRegistry.laneKernels("rotate");
        assertEquals(LaneKernelRegistry.ALL_LANES.size(), kernels.size());
        for (String label : LaneKernelRegistry.ALL_LANES) {
            assertTrue(kernels.containsKey(label), "label " + label + " must be present");
        }
        // A..Z + e all resolve to the rotate kernel
        LaneResult r = kernels.get("A").apply(new String[] {"A"}, "idle", null);
        assertEquals("rotate", r.action());
        LaneResult e = kernels.get("e").apply(new String[] {"e"}, "idle", null);
        assertEquals("rotate", e.action());
    }

    @Test
    void specMapPartialDefaultsToIdentity() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("A", "gamma");
        spec.put("B", LaneKernel.IDENTITY);
        Map<String, LaneKernel> kernels = LaneKernelRegistry.laneKernels(spec);

        assertEquals("gamma", kernels.get("A").apply(new String[] {"A"}, "idle", null).action());
        assertEquals("identity", kernels.get("B").apply(new String[] {"B"}, "idle", null).action());
        // missing labels default to identity
        assertEquals("identity", kernels.get("C").apply(new String[] {"C"}, "idle", null).action());
        assertEquals("identity", kernels.get("e").apply(new String[] {"e"}, "idle", null).action());
    }

    @Test
    void specRejectsBadEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> LaneKernelRegistry.laneKernels(42));
        Map<String, Object> unnamed = new LinkedHashMap<>();
        unnamed.put("", "gamma");
        assertThrows(IllegalArgumentException.class,
                () -> LaneKernelRegistry.laneKernels(unnamed));
        Map<String, Object> badVal = new LinkedHashMap<>();
        badVal.put("A", 42);
        assertThrows(IllegalArgumentException.class,
                () -> LaneKernelRegistry.laneKernels(badVal));
    }
}
