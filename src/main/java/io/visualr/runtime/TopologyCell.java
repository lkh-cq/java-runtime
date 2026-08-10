package io.visualr.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TopologyCell — the real identity behind a 3x3 view (ABI v0.1 §2.1).
 *
 * <p>Java mirror of the R {@code visualr_topology_cell} built by
 * {@code new_topology_cell}: one singularity, four (or more) orbits
 * each with two endpoint tokens, plus phase/orientation/origin/payload
 * metadata. The 3x3 matrix is a PROJECTION of this cell, never the
 * object itself.</p>
 *
 * <p>Restoration from PAL is one-step ({@link #palToCell(PalState)}),
 * mirroring R {@code pal_to_cell}: singularity, orbit endpoints,
 * containment order and origin are recovered from the generative PAL
 * encoding — never char-by-char.</p>
 */
public final class TopologyCell {

    private final String singularity;
    private final LinkedHashMap<String, String[]> orbits; // label -> [head, tail]
    private final String phase;
    private final String orientation;
    private final Map<String, Object> origin;
    private final Map<String, Object> payload;

    private TopologyCell(String singularity, LinkedHashMap<String, String[]> orbits,
                         String phase, String orientation,
                         Map<String, Object> origin, Map<String, Object> payload) {
        this.singularity = singularity;
        this.orbits = orbits;
        this.phase = phase;
        this.orientation = orientation;
        this.origin = Collections.unmodifiableMap(new LinkedHashMap<>(origin));
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    /** Constructor with validation (mirror of R {@code new_topology_cell}). */
    public static TopologyCell of(String singularity, Map<String, String[]> orbits,
                                  String phase, String orientation,
                                  Map<String, Object> origin, Map<String, Object> payload) {
        if (singularity == null || singularity.isEmpty()) {
            throw new IllegalArgumentException("core must be a single non-NA character");
        }
        if (orbits == null) {
            throw new IllegalArgumentException("orbits must be a named map");
        }
        LinkedHashMap<String, String[]> ordered = new LinkedHashMap<>(orbits);
        for (Map.Entry<String, String[]> e : ordered.entrySet()) {
            String nm = e.getKey();
            String[] ep = e.getValue();
            if (nm == null || nm.isEmpty()) {
                throw new IllegalArgumentException("orbit names must be non-empty");
            }
            if (ep == null || ep.length != 2) {
                throw new IllegalArgumentException("orbit " + nm + " must be a 2-endpoint array");
            }
            // NA endpoints allowed (missing orbit = shallow PAL); partial NA rejected.
            boolean bothNull = ep[0] == null && ep[1] == null;
            boolean anyNull = ep[0] == null || ep[1] == null;
            if (anyNull && !bothNull) {
                throw new IllegalArgumentException("orbit " + nm + ": endpoint has partial null");
            }
        }
        return new TopologyCell(singularity, ordered,
                phase == null ? "idle" : phase,
                orientation == null ? "canonical" : orientation,
                origin == null ? new LinkedHashMap<>() : origin,
                payload == null ? new LinkedHashMap<>() : payload);
    }

    /**
     * ONE-STEP restoration from a PAL state (mirror of R {@code pal_to_cell}).
     * Orbit labels are A, B, C, D, E, ... (outermost first); S_5+ extends
     * the label sequence so no shell is dropped.
     */
    public static TopologyCell palToCell(PalState pal) {
        List<String> shells = pal.shells();
        int nShells = shells.size();
        Map<String, String[]> orbits = new LinkedHashMap<>();
        if (nShells >= 1) {
            for (int i = 0; i < nShells; i++) {
                String label = orbitLabel(i, nShells);
                String tok = shells.get(i);
                orbits.put(label, new String[] {tok, tok}); // head == tail for canonical PAL
            }
        } else {
            orbits.put("A", new String[] {null, null});
            orbits.put("B", new String[] {null, null});
            orbits.put("C", new String[] {null, null});
            orbits.put("D", new String[] {null, null});
        }
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("pal", PalCodec.format(pal));
        origin.put("dim", nShells);
        return of(pal.core(), orbits, "idle", "canonical", origin, new LinkedHashMap<>());
    }

    private static String orbitLabel(int idx, int nShells) {
        if (nShells <= 26) {
            return String.valueOf((char) ('A' + idx));
        }
        return String.format("O%02d", idx + 1);
    }

    /** Unfold PAL into the full palindrome token sequence: shells + core + rev(shells). */
    public static List<String> unfold(PalState pal) {
        List<String> unfolded = new ArrayList<>(pal.shells());
        unfolded.add(pal.core());
        List<String> rev = new ArrayList<>(pal.shells());
        Collections.reverse(rev);
        unfolded.addAll(rev);
        return unfolded;
    }

    public String singularity() { return singularity; }

    /** R compatibility alias: the constructor arg is named {@code core}. */
    public String core() { return singularity; }

    /** Ordered orbit map: label -> [head, tail]. */
    public Map<String, String[]> orbits() { return orbits; }

    public String phase() { return phase; }

    public String orientation() { return orientation; }

    public Map<String, Object> origin() { return origin; }

    public Map<String, Object> payload() { return payload; }

    @Override
    public String toString() {
        return String.format("<visualr_topology_cell> singularity=%s phase=%s orbits=%d",
                singularity, phase, orbits.size());
    }
}
