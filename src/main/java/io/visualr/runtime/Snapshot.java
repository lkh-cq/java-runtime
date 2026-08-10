package io.visualr.runtime;

import java.util.Map;

/**
 * Snapshot — freezes the current carrier state (ABI v0.1 §5.2).
 *
 * <p>Java mirror of the R {@code snapshot}: freezes S_t including the
 * active mask so concurrent lanes read the SAME view. The carrier
 * itself is not mutated by lanes (snapshot-commit transaction, never
 * multi-threaded writes into one matrix).</p>
 */
public final class Snapshot {

    private final TopologyCell cell;
    private final Map<String, Object> topologyMap;
    private final boolean[][] activeMask;
    private final int[][] projection;
    private final boolean frozen;

    private Snapshot(TopologyCell cell, Map<String, Object> topologyMap,
                     boolean[][] activeMask, int[][] projection) {
        this.cell = cell;
        this.topologyMap = topologyMap;
        this.activeMask = activeMask;
        this.projection = projection;
        this.frozen = true;
    }

    /** Freeze a carrier into a snapshot (mirror of R {@code snapshot}). */
    public static Snapshot of(TopologyCarrier carrier) {
        if (carrier == null) {
            throw new IllegalArgumentException("carrier must be a visualr_carrier");
        }
        return new Snapshot(carrier.cell(), carrier.topologyMap(),
                carrier.activeMask(), carrier.projection());
    }

    public TopologyCell cell() { return cell; }

    public Map<String, Object> topologyMap() { return topologyMap; }

    public boolean[][] activeMask() { return activeMask; }

    public int[][] projection() { return projection; }

    public boolean frozen() { return frozen; }

    @Override
    public String toString() {
        return String.format("<visualr_snapshot> frozen=%s singularity=%s",
                frozen, cell.singularity());
    }
}
