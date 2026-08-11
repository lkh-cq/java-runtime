package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure-Java semantics for the Topology Operator ABI v0.1 pipeline
 * (lanes / barrier / reconcile / commit / cell-to-PAL).
 */
class TopologyOperatorTest {

    private static PalState sample4() {
        return PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static PalState emptyShells() {
        return PalState.of(List.of(), "e", PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    @Test
    void executeLanesDefaultIdentity() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4());
        Snapshot snap = Snapshot.of(carrier);
        Map<String, LaneResult> deltas = TopologyOperator.executeLanes(snap);

        assertEquals(List.of("A", "B", "C", "D", "e"), List.copyOf(deltas.keySet()));
        // identity: orbit endpoints preserved
        assertEquals("A", deltas.get("A").endpoints()[0]);
        assertEquals("D", deltas.get("D").endpoints()[1]);
        // singularity lane receives the core
        assertEquals("e", deltas.get("e").endpoints()[0]);
        assertEquals("idle", deltas.get("e").phase());
        assertEquals("identity", deltas.get("A").action());
    }

    @Test
    void barrierPassesAndErrors() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4());
        Snapshot snap = Snapshot.of(carrier);
        Map<String, LaneResult> deltas = TopologyOperator.executeLanes(snap);
        assertTrue(TopologyOperator.barrier(deltas));

        Map<String, LaneResult> missingE = new LinkedHashMap<>(deltas);
        missingE.remove("e");
        assertThrows(IllegalArgumentException.class, () -> TopologyOperator.barrier(missingE));

        assertThrows(IllegalArgumentException.class, () -> TopologyOperator.barrier(null));
    }

    @Test
    void reconcileIdentityPromotesToIdle() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4());
        Snapshot snap = Snapshot.of(carrier);
        Map<String, LaneResult> deltas = TopologyOperator.executeLanes(snap);
        ReconcileResult rec = TopologyOperator.reconcile(deltas, carrier.cell());

        assertTrue(rec.ok());
        assertTrue(rec.conflicts().isEmpty());
        assertEquals("promote", rec.action());
        assertEquals("idle", rec.phase());
        assertEquals(4, rec.reconciledCell().orbits().size());
        assertEquals("e", rec.reconciledCell().singularity());
    }

    @Test
    void reconcileRejectsWithoutOrbitLanes() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4());
        Map<String, LaneResult> onlyE = new LinkedHashMap<>();
        onlyE.put("e", LaneResult.of(new String[] {"e"}, "idle", "identity"));
        ReconcileResult rec = TopologyOperator.reconcile(onlyE, carrier.cell());
        assertFalse(rec.ok());
        assertEquals("reject", rec.action());
        assertTrue(rec.conflicts().contains("no orbit lanes"));
    }

    @Test
    void customKernelDrivesPhaseTransition() {
        // Non-canonical asymmetric orbits so a rotate kernel is observable.
        Map<String, String[]> orbits = new LinkedHashMap<>();
        orbits.put("A", new String[] {"x", "y"});
        orbits.put("B", new String[] {"b", "b"});
        orbits.put("C", new String[] {"c", "c"});
        orbits.put("D", new String[] {"d", "d"});
        TopologyCell cell = TopologyCell.of("e", orbits, "running", "canonical",
                Map.of(), Map.of());
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4(), cell,
                List.of("space", "operator", "phase", "channel"), null);
        Snapshot snap = Snapshot.of(carrier);

        Map<String, LaneKernel> kernels = new LinkedHashMap<>();
        // A rotates the endpoint pair; others identity.
        kernels.put("A", (ep, phase, pack) -> LaneResult.of(
                new String[] {ep[1], ep[0]}, phase, "rotate"));
        Map<String, LaneResult> deltas = TopologyOperator.executeLanes(snap, kernels);

        assertEquals("y", deltas.get("A").endpoints()[0]); // rotated
        ReconcileResult rec = TopologyOperator.reconcile(deltas, carrier.cell());
        assertTrue(rec.ok());
        // not all identity -> phase stays "running" (no transition to idle)
        assertEquals("running", rec.phase());
        assertEquals("y", rec.reconciledCell().orbits().get("A")[0]);
    }

    @Test
    void commitFailClosed() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4());
        Map<String, LaneResult> onlyE = new LinkedHashMap<>();
        onlyE.put("e", LaneResult.of(new String[] {"e"}, "idle", "identity"));
        ReconcileResult rejected = TopologyOperator.reconcile(onlyE, carrier.cell());
        assertThrows(IllegalStateException.class,
                () -> TopologyOperator.commit(rejected, carrier));

        Snapshot snap = Snapshot.of(carrier);
        ReconcileResult rec = TopologyOperator.reconcile(
                TopologyOperator.executeLanes(snap), carrier.cell());
        TopologyCarrier out = TopologyOperator.commit(rec, carrier);
        assertEquals("e", out.cell().singularity());
        assertEquals(carrier.axes(), out.axes());
    }

    @Test
    void cellToPalRoundTrip() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(sample4());
        PalState back = TopologyOperator.cellToPal(carrier.cell(), sample4());
        assertEquals(List.of("A", "B", "C", "D"), back.shells());
        assertEquals("e", back.core());
        assertEquals(sample4().mappingPackId(), back.mappingPackId());
    }

    @Test
    void cellToPalSkipsNaOrbits() {
        TopologyCarrier carrier = TopologyCarrier.fromPal(emptyShells());
        // empty-shell PAL restores A/B/C/D all-NA orbits -> re-encode drops them
        PalState back = TopologyOperator.cellToPal(carrier.cell(), emptyShells());
        assertTrue(back.shells().isEmpty());
        assertEquals("e", back.core());
    }

    @Test
    void fullPipelineIdentityKeepsPal() {
        PalState pal = sample4();
        PipelineResult res = TopologyOperator.runPipeline(pal);
        assertTrue(res.barrierOk());
        assertTrue(res.reconciled().ok());
        assertEquals("promote", res.reconciled().action());
        // identity pipeline: S_(t+1) PAL re-encodes to the same canonical state
        assertEquals(PalCodec.format(pal), PalCodec.format(res.palOut()));
    }

    @Test
    void runPipelineUnknownPackFailsClosed() {
        // gate review P1-2: unknown mapping pack id must error in the
        // pipeline, not silently degrade to default semantics
        PalState bad = PalState.of(List.of("A", "B", "C", "D"), "e",
                "nonexistent-pack", Map.of());
        assertThrows(IllegalArgumentException.class, () -> TopologyOperator.runPipeline(bad));
    }
}
