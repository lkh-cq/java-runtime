package io.visualr.runtime;

import java.util.Map;

/**
 * Full pipeline result — mirror of the R {@code run_topology_pipeline}
 * return list: {@code carrier_in, snapshot, deltas, barrier,
 * reconciled, carrier_out, pal_out}.
 *
 * @param carrierIn   the carrier built from the input PAL (S_t)
 * @param snapshot    frozen snapshot read by every lane
 * @param deltas      per-lane results A/B/C/D/e
 * @param barrierOk   true (barrier errors instead of returning false)
 * @param reconciled  reconcile outcome
 * @param carrierOut  committed carrier (S_(t+1))
 * @param palOut      re-encoded canonical PAL of S_(t+1)
 */
public record PipelineResult(TopologyCarrier carrierIn, Snapshot snapshot,
                             Map<String, LaneResult> deltas, boolean barrierOk,
                             ReconcileResult reconciled, TopologyCarrier carrierOut,
                             PalState palOut) {
}
