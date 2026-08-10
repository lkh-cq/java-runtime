package io.visualr.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TopologyCarrier — the high-dimensional working object (ABI v0.1 §2.2).
 *
 * <p>Java mirror of the R {@code visualr_carrier} built by
 * {@code new_topology_carrier}: axes, topology map, active mask, payload.
 * Holds FULL dimension semantics — linear addresses are just memory; the
 * carrier keeps the coordinate -&gt; topology mapping so nothing is
 * flattened (semantic flattening is forbidden; only execution lowering
 * is legal).</p>
 *
 * <p>Axis model: X[x,y,z,o,phi,c] — (x,y,z) space, (o) operator,
 * (phi) phase, (c) channel.</p>
 */
public final class TopologyCarrier {

    private final PalState pal;
    private final TopologyCell cell;
    private final List<String> axes;
    private final Map<String, Object> topologyMap; // singularity + orbits
    private final boolean[][] activeMask;          // 3x3 canonical carrier
    private final int[][] projection;              // optional 3x3 jiugong view
    private final Map<String, Object> payload;

    private TopologyCarrier(PalState pal, TopologyCell cell, List<String> axes,
                            Map<String, Object> topologyMap, boolean[][] activeMask,
                            int[][] projection, Map<String, Object> payload) {
        this.pal = pal;
        this.cell = cell;
        this.axes = Collections.unmodifiableList(axes);
        this.topologyMap = Collections.unmodifiableMap(new LinkedHashMap<>(topologyMap));
        this.activeMask = activeMask;
        this.projection = projection;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** Build a carrier from a PAL state (mirror of R {@code new_topology_carrier}). */
    public static TopologyCarrier fromPal(PalState pal) {
        return fromPal(pal, null, null, null);
    }

    /**
     * Build a carrier (mirror of R {@code new_topology_carrier}).
     *
     * @param pal        source of truth
     * @param cell       restored cell; null -&gt; {@link TopologyCell#palToCell}
     * @param axes       axis names; null -&gt; space/operator/phase/channel
     * @param projection optional 3x3 view; null -&gt; skipped in this slice
     *                   (R computes pal_to_jiugong grid; Java projection
     *                   materialization lands in a later slice)
     */
    public static TopologyCarrier fromPal(PalState pal, TopologyCell cell,
                                          List<String> axes, int[][] projection) {
        TopologyCell c = (cell != null) ? cell : TopologyCell.palToCell(pal);
        List<String> ax = (axes != null) ? axes
                : List.of("space", "operator", "phase", "channel");

        // topology_map: singularity index in the unfolded sequence +
        // one entry per orbit (head/tail indices left unresolved here,
        // mirroring R's NA placeholders).
        List<String> unfolded = TopologyCell.unfold(pal);
        int n = unfolded.size();
        int half = (n - 1) / 2;

        Map<String, Object> topo = new LinkedHashMap<>();
        Map<String, Object> sing = new LinkedHashMap<>();
        sing.put("index", half + 1);
        sing.put("token", pal.core());
        topo.put("singularity", sing);
        Map<String, Object> orbits = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : c.orbits().entrySet()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", e.getKey());
            o.put("head_idx", null);
            o.put("tail_idx", null);
            orbits.put(e.getKey(), o);
        }
        topo.put("orbits", orbits);

        // active_mask: all cells active for a canonical carrier (future:
        // inactive regions stay frozen).
        boolean[][] mask = new boolean[3][3];
        for (boolean[] row : mask) {
            Arrays.fill(row, true);
        }

        return new TopologyCarrier(pal, c, ax, topo, mask, projection,
                new LinkedHashMap<>());
    }

    public PalState pal() { return pal; }

    public TopologyCell cell() { return cell; }

    public List<String> axes() { return axes; }

    public Map<String, Object> topologyMap() { return topologyMap; }

    public boolean[][] activeMask() { return activeMask; }

    public int[][] projection() { return projection; }

    public Map<String, Object> payload() { return payload; }

    /** Total active cells (sum of the active mask). */
    public int activeCount() {
        int sum = 0;
        for (boolean[] row : activeMask) {
            for (boolean b : row) {
                if (b) sum++;
            }
        }
        return sum;
    }

    @Override
    public String toString() {
        return String.format("<visualr_carrier> axes=%s singularity=%s orbits=%d active=%d",
                String.join(",", axes), cell.singularity(), cell.orbits().size(), activeCount());
    }
}
