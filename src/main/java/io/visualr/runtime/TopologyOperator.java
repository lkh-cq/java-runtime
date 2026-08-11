package io.visualr.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Topology Operator ABI v0.1 — Java execution fabric (mirror of R
 * {@code topology_carrier.R}).
 *
 * <p>Pipeline: {@code TopologyCarrier -> Snapshot -> Concurrent Lanes
 * -> Barrier -> Reconcile -> Commit -> PAL re-encoding}.</p>
 *
 * <p>R remains the authoritative semantics. This class is the Java
 * orchestration/execution layer: lanes execute concurrently over the
 * SAME frozen snapshot (one logical instant), reconcile is topological
 * (never numeric reduction), commit is fail-closed (non-closed states
 * cannot enter canonical storage).</p>
 *
 * <p>Known R behavior mirrored verbatim: the default kernel set is
 * fixed A/B/C/D/e — with identity kernels a deeper PAL (S_5+) therefore
 * keeps only the first four orbit lanes in the deltas, exactly like R's
 * {@code execute_lanes}. String kernel-name specs
 * ({@code execute_lanes_ops}) are a later slice.</p>
 */
public final class TopologyOperator {

    /** Default lane names: four orbit lanes + the singularity lane. */
    public static final List<String> DEFAULT_LANES = List.of("A", "B", "C", "D", "e");

    private TopologyOperator() {}

    // =====================================================================
    // Concurrent Lanes (ABI §5.3)
    // =====================================================================

    /**
     * Execute the concurrent lanes over a snapshot (mirror of R
     * {@code execute_lanes_ops} — the dynamic version used by
     * {@code run_topology_pipeline}). ONE lane per orbit (label order)
     * plus the singularity lane: S_4 -&gt; A/B/C/D + e; S_5 -&gt;
     * A/B/C/D/E + e. Every lane reads the SAME snapshot at the SAME
     * logical instant; lanes are pure, so they run in parallel with no
     * shared-state writes.
     *
     * @param snap    frozen snapshot
     * @param kernels lane kernels keyed by lane name; null -&gt; all identity
     */
    public static Map<String, LaneResult> executeLanes(Snapshot snap,
                                                       Map<String, LaneKernel> kernels) {
        if (snap == null) {
            throw new IllegalArgumentException("snap must be a visualr_snapshot");
        }
        TopologyCell cell = snap.cell();
        List<String> laneNames = new ArrayList<>(cell.orbits().keySet());
        laneNames.add("e");
        String phase = cell.phase();
        // Resolve the mapping pack for this cell (mirror of R
        // execute_lanes_ops tryCatch(pal_resolve_pack(...), NULL)).
        // NOTE: R's current implementation passes the FORMAT STRING in
        // cell$origin$pal, so R's pack is always NULL (a latent R bug);
        // Java resolves the DESIGN INTENT — parse the stored PAL, resolve
        // its mapping_pack_id. Default-pack behavior is identical.
        Map<String, Object> pack = resolvePackMap(cell);

        Map<String, LaneKernel> ks = new LinkedHashMap<>();
        if (kernels != null) {
            for (String name : laneNames) {
                ks.put(name, kernels.getOrDefault(name, LaneKernel.IDENTITY));
            }
        }

        // One logical instant: submit all lanes, then gather in lane order.
        ExecutorService pool = Executors.newFixedThreadPool(laneNames.size());
        try {
            Map<String, CompletableFuture<LaneResult>> futures = new LinkedHashMap<>();
            for (String name : laneNames) {
                String[] input = laneInput(cell, name);
                LaneKernel kernel = (kernels == null) ? LaneKernel.IDENTITY : ks.get(name);
                futures.put(name, CompletableFuture.supplyAsync(() -> kernel.apply(input, phase, pack), pool));
            }
            Map<String, LaneResult> deltas = new LinkedHashMap<>();
            for (String name : laneNames) {
                deltas.put(name, futures.get(name).join());
            }
            return deltas;
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Resolve the mapping pack for a PAL state by its mapping_pack_id
     * (mirror of R {@code pal_resolve_pack}; FAILS CLOSED on unknown id).
     */
    public static MappingPack palResolvePack(PalState pal) {
        return MappingPackRegistry.resolve(pal.mappingPackId());
    }

    /** Resolve the pack from a cell's stored origin PAL; null when unavailable. */
    private static Map<String, Object> resolvePackMap(TopologyCell cell) {
        try {
            Object stored = cell.origin().get("pal");
            if (stored instanceof String s) {
                MappingPack mp = MappingPackRegistry.resolve(PalCodec.parse(s).mappingPackId());
                return packToMap(mp);
            }
        } catch (RuntimeException ignored) {
            // mirror R tryCatch(..., error = function(e) NULL)
        }
        return null;
    }

    /** Flatten a MappingPack to the kernel-visible map (R pack$<field> access). */
    private static Map<String, Object> packToMap(MappingPack pack) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("complement_table", pack.complementTable());
        m.put("orbit_table", pack.orbitTable());
        m.put("expand_order", pack.expandOrder());
        m.put("frozen_symbols", pack.frozenSymbols());
        m.put("version", pack.version());
        return m;
    }

    /** Default execution: all lanes identity (mirror of R {@code execute_lanes_ops(snap)}). */
    public static Map<String, LaneResult> executeLanes(Snapshot snap) {
        return executeLanes(snap, (Map<String, LaneKernel>) null);
    }

    /**
     * Kernel-name convenience: one kernel for ALL lanes
     * (mirror of R {@code execute_lanes_ops(snap, "rotate")}).
     */
    public static Map<String, LaneResult> executeLanes(Snapshot snap, String kernelName) {
        return executeLanes(snap, LaneKernelRegistry.laneKernels(kernelName));
    }

    /**
     * Kernel-spec convenience: named list of kernel names or functions;
     * missing labels default to identity (mirror of R
     * {@code execute_lanes_ops(snap, list(A = "gamma", ...))}).
     */
    public static Map<String, LaneResult> executeLanesSpec(Snapshot snap,
                                                           Map<String, Object> kernelSpec) {
        return executeLanes(snap, LaneKernelRegistry.laneKernels(kernelSpec));
    }

    /**
     * Fixed-lane variant (mirror of R {@code execute_lanes}): the default
     * kernel set is exactly A/B/C/D/e — with identity kernels a deeper
     * PAL (S_5+) keeps only the first four orbit lanes in the deltas,
     * exactly like R's fixed version.
     */
    public static Map<String, LaneResult> executeLanesFixed(Snapshot snap,
                                                            Map<String, LaneKernel> kernels) {
        if (snap == null) {
            throw new IllegalArgumentException("snap must be a visualr_snapshot");
        }
        Map<String, LaneKernel> ks = new LinkedHashMap<>();
        if (kernels == null) {
            for (String name : DEFAULT_LANES) {
                ks.put(name, LaneKernel.IDENTITY);
            }
        } else {
            for (String name : DEFAULT_LANES) {
                ks.put(name, kernels.getOrDefault(name, LaneKernel.IDENTITY));
            }
        }

        TopologyCell cell = snap.cell();
        String phase = cell.phase();

        ExecutorService pool = Executors.newFixedThreadPool(ks.size());
        try {
            Map<String, CompletableFuture<LaneResult>> futures = new LinkedHashMap<>();
            for (String name : DEFAULT_LANES) {
                String[] input = laneInput(cell, name);
                LaneKernel kernel = ks.get(name);
                futures.put(name, CompletableFuture.supplyAsync(() -> kernel.apply(input, phase, null), pool));
            }
            Map<String, LaneResult> deltas = new LinkedHashMap<>();
            for (String name : DEFAULT_LANES) {
                deltas.put(name, futures.get(name).join());
            }
            return deltas;
        } finally {
            pool.shutdown();
        }
    }

    private static String[] laneInput(TopologyCell cell, String name) {
        if ("e".equals(name)) {
            return new String[] {cell.singularity()};
        }
        String[] ep = cell.orbits().get(name);
        if (ep == null) {
            // Mirror R: cell$orbits$<label> for a label absent from the
            // cell (deeper shells than the default lanes) is a missing
            // orbit — identity passes a null pair through.
            return new String[] {null, null};
        }
        return ep.clone();
    }

    // =====================================================================
    // Barrier (ABI §5.3)
    // =====================================================================

    /**
     * Barrier: all lanes must have produced a well-formed delta (mirror
     * of R {@code barrier}). Errors on malformed deltas.
     */
    public static boolean barrier(Map<String, LaneResult> deltas) {
        if (deltas == null) {
            throw new IllegalArgumentException("barrier: deltas must be a map");
        }
        for (Map.Entry<String, LaneResult> e : deltas.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) {
                throw new IllegalArgumentException("barrier: deltas must have unique non-empty names");
            }
            if (e.getValue() == null) {
                throw new IllegalArgumentException("barrier: lane " + e.getKey() + " produced no result");
            }
        }
        if (!deltas.containsKey("e")) {
            throw new IllegalArgumentException("barrier: missing singularity lane 'e'");
        }
        return true;
    }

    // =====================================================================
    // Reconcile (ABI §5.4 — topological, NOT numeric reduction)
    // =====================================================================

    /**
     * Reconcile lane deltas into a coherent state (mirror of R
     * {@code reconcile}). Conflict kinds: determinable (lanes agree —
     * accept), conflicting values (fail-closed reject), same value
     * different position (flag), phase transition, closure.
     */
    public static ReconcileResult reconcile(Map<String, LaneResult> deltas,
                                            TopologyCell cell, String mappingPackId) {
        List<String> laneNames = new ArrayList<>(deltas.keySet());
        laneNames.remove("e");

        if (laneNames.isEmpty()) {
            return ReconcileResult.rejected(List.of("no orbit lanes"), cell);
        }

        List<String> conflicts = new ArrayList<>();
        for (String nm : laneNames) {
            LaneResult res = deltas.get(nm);
            if (res == null || res.endpoints() == null) {
                conflicts.add(nm + ": malformed lane result");
            }
        }
        if (!conflicts.isEmpty()) {
            return ReconcileResult.rejected(conflicts, cell);
        }

        // Build reconciled cell from lane results (dynamic orbit count).
        Map<String, String[]> newOrbits = new LinkedHashMap<>();
        for (String nm : laneNames) {
            newOrbits.put(nm, deltas.get(nm).endpoints());
        }
        TopologyCell reconciled = TopologyCell.of(
                cell.singularity(), newOrbits,
                cell.phase(), cell.orientation(), cell.origin(), cell.payload());

        // Phase transition: when all lanes stable (identity), phase idles.
        String phase = cell.phase();
        boolean allIdentity = true;
        for (String nm : laneNames) {
            if (!"identity".equals(deltas.get(nm).action())) {
                allIdentity = false;
                break;
            }
        }
        if (allIdentity) {
            phase = "idle";
        }
        return ReconcileResult.promoted(phase, reconciled);
    }

    // =====================================================================
    // Commit (ABI §5.5 — S_(t+1) / fail-closed)
    // =====================================================================

    /**
     * Commit a reconciled state back to a carrier for S_(t+1) (mirror of
     * R {@code commit}). Non-closed states are REJECTED (fail-closed):
     * they cannot enter canonical storage silently.
     */
    public static TopologyCarrier commit(ReconcileResult reconciled, TopologyCarrier carrier) {
        if (reconciled == null || !reconciled.ok()) {
            throw new IllegalStateException("commit: cannot commit non-reconciled state (fail-closed)");
        }
        if (carrier == null) {
            throw new IllegalArgumentException("carrier must be a visualr_carrier");
        }
        if ("reject".equals(reconciled.action())) {
            throw new IllegalStateException("commit: reconciled state rejected (fail-closed)");
        }
        // S_(t+1): new carrier with the reconciled cell, same pal lineage.
        return TopologyCarrier.fromPal(carrier.pal(), reconciled.reconciledCell(),
                carrier.axes(), carrier.projection());
    }

    // =====================================================================
    // Carrier -> PAL re-encoding (closed-loop final link)
    // =====================================================================

    /**
     * Re-encode a reconciled cell back to a PAL state (mirror of R
     * {@code cell_to_pal}). Orbits read outer-to-inner (A,B,C,D,...) and
     * dropped when NA (missing), preserving containment order; the
     * singularity becomes the core. mapping_pack_id/provenance continue
     * from the original pal.
     */
    public static PalState cellToPal(TopologyCell cell, PalState palOrig) {
        List<String> shells = new ArrayList<>();
        for (Map.Entry<String, String[]> e : cell.orbits().entrySet()) {
            String[] ep = e.getValue();
            if (ep != null && ep[0] != null && ep[1] != null) {
                shells.add(ep[0]); // head token (canonical: head == tail)
            }
        }
        return PalState.of(shells, cell.singularity(),
                palOrig.mappingPackId(), palOrig.provenance());
    }

    // =====================================================================
    // Full pipeline driver
    // =====================================================================

    /**
     * One-command driver: PAL -&gt; TopologyCarrier -&gt; Snapshot -&gt;
     * Concurrent Lanes -&gt; Barrier -&gt; Reconcile -&gt; Commit -&gt;
     * PAL re-encoding (S_(t+1)). Mirror of R {@code run_topology_pipeline}
     * with the {@code execute_lanes} (map-kernel) path.
     */
    public static PipelineResult runPipeline(PalState pal,
                                             Map<String, LaneKernel> kernels) {
        TopologyCarrier carrierIn = TopologyCarrier.fromPal(pal);
        Snapshot snap = Snapshot.of(carrierIn);
        Map<String, LaneResult> deltas = executeLanes(snap, kernels);
        boolean barrierOk = barrier(deltas);
        ReconcileResult rec = reconcile(deltas, carrierIn.cell(), pal.mappingPackId());
        TopologyCarrier carrierOut = commit(rec, carrierIn);
        PalState palOut = cellToPal(carrierOut.cell(), pal);
        return new PipelineResult(carrierIn, snap, deltas, barrierOk, rec, carrierOut, palOut);
    }

    /** Pipeline with default identity kernels (mirror of R {@code run_topology_pipeline(pal)}). */
    public static PipelineResult runPipeline(PalState pal) {
        return runPipeline(pal, (Map<String, LaneKernel>) null);
    }

    /** Pipeline with a single kernel name for all lanes (e.g. "rotate"). */
    public static PipelineResult runPipeline(PalState pal, String kernelName) {
        return runPipeline(pal, LaneKernelRegistry.laneKernels(kernelName));
    }

    /** Pipeline with a kernel spec map (names or functions, identity default). */
    public static PipelineResult runPipelineSpec(PalState pal, Map<String, Object> kernelSpec) {
        return runPipeline(pal, LaneKernelRegistry.laneKernels(kernelSpec));
    }
}
