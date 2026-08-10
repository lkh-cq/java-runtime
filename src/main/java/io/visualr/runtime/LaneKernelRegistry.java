package io.visualr.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lane kernel registry — mirror of R {@code .lane_kernel_env} +
 * {@code lane_kernel_get} / {@code lane_kernel_list} / {@code lane_kernels}
 * (orbit_operators.R).
 *
 * <p>Built-in kernels (all faithfully ported from R):</p>
 * <ul>
 *   <li>{@code identity} — orbit unchanged</li>
 *   <li>{@code complement} — self-mirror involution A&lt;-&gt;D, B&lt;-&gt;C, e-&gt;e</li>
 *   <li>{@code mirror} — swap endpoints (singularity lane stays itself)</li>
 *   <li>{@code rotate} — orbit-table rotation A-&gt;B-&gt;C-&gt;D-&gt;A</li>
 *   <li>{@code gamma} — local field step A-&gt;B-&gt;C-&gt;D-&gt;e (clamped at e)</li>
 * </ul>
 *
 * <p>Token mapping rules mirror R: NA/null endpoints pass through, tokens
 * outside the table pass through unchanged. Unknown kernel names FAIL
 * CLOSED (mirror of R {@code stop} in {@code lane_kernel_get}).</p>
 */
public final class LaneKernelRegistry {

    /** Complement table: A&lt;-&gt;D, B&lt;-&gt;C, e-&gt;e (R default). */
    public static final Map<String, String> COMPLEMENT_TABLE = Map.of(
            "A", "D", "D", "A", "B", "C", "C", "B", "e", "e");

    /** Rotate cycle: A-&gt;B-&gt;C-&gt;D-&gt;A (R kernel_rotate). */
    public static final Map<String, String> ROTATE_CYCLE = Map.of(
            "A", "B", "B", "C", "C", "D", "D", "A");

    /** Gamma ladder: A-&gt;B-&gt;C-&gt;D-&gt;e (clamped, R kernel_gamma). */
    public static final Map<String, String> GAMMA_LADDER = Map.of(
            "A", "B", "B", "C", "C", "D", "D", "e", "e", "e");

    /** All lane labels A..Z plus the singularity lane label. */
    public static final List<String> ALL_LANES;

    static {
        List<String> labels = new ArrayList<>(26);
        for (int i = 0; i < 26; i++) {
            labels.add(String.valueOf((char) ('A' + i)));
        }
        labels.add("e");
        ALL_LANES = List.copyOf(labels);
    }

    private static final Map<String, LaneKernel> REGISTRY = new LinkedHashMap<>();

    static {
        REGISTRY.put("identity", LaneKernel.IDENTITY);
        REGISTRY.put("complement", (ep, phase, pack) -> {
            Map<String, String> tbl = pack != null && pack.get("complement_table") instanceof Map<?, ?> m
                    ? toStringMap(m) : COMPLEMENT_TABLE;
            return LaneResult.of(mapTokens(ep, tbl), phase, "complement");
        });
        REGISTRY.put("mirror", (ep, phase, pack) -> {
            if (ep.length == 1) {
                return LaneResult.of(ep, phase, "mirror"); // singularity: center mirrors itself
            }
            return LaneResult.of(new String[] {ep[1], ep[0]}, phase, "mirror");
        });
        REGISTRY.put("rotate", (ep, phase, pack) ->
                LaneResult.of(mapTokens(ep, ROTATE_CYCLE), phase, "rotate"));
        REGISTRY.put("gamma", (ep, phase, pack) ->
                LaneResult.of(mapTokens(ep, GAMMA_LADDER), phase, "gamma"));
    }

    private LaneKernelRegistry() {}

    /** Get a kernel by name; unknown names fail closed (mirror of R). */
    public static LaneKernel get(String name) {
        LaneKernel k = REGISTRY.get(name);
        if (k == null) {
            throw new IllegalArgumentException("Unknown lane kernel '" + name + "' (fail closed)");
        }
        return k;
    }

    /** Sorted names of all registered kernels (mirror of R lane_kernel_list). */
    public static List<String> list() {
        List<String> names = new ArrayList<>(REGISTRY.keySet());
        names.sort(String::compareTo);
        return names;
    }

    /**
     * Resolve a kernel spec (mirror of R {@code lane_kernels}):
     * <ul>
     *   <li>{@code String} — one kernel for ALL lanes (A..Z + e); deeper
     *       states pick the subset they need.</li>
     *   <li>{@code Map<String,Object>} — entries are kernel NAMES (String)
     *       or {@link LaneKernel} functions; missing labels default to
     *       identity so partial specs work for any depth.</li>
     * </ul>
     */
    public static Map<String, LaneKernel> laneKernels(Object spec) {
        Map<String, LaneKernel> out = new LinkedHashMap<>();
        if (spec instanceof String name) {
            LaneKernel fn = get(name);
            for (String label : ALL_LANES) {
                out.put(label, fn);
            }
            return out;
        }
        if (spec instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!(e.getKey() instanceof String label) || label.isEmpty()) {
                    throw new IllegalArgumentException("lane spec entries must be named");
                }
                Object v = e.getValue();
                if (v instanceof String s) {
                    out.put(label, get(s));
                } else if (v instanceof LaneKernel k) {
                    out.put(label, k);
                } else {
                    throw new IllegalArgumentException(
                            "lane spec entries must be kernel names or LaneKernel functions");
                }
            }
            for (String label : ALL_LANES) {
                out.putIfAbsent(label, LaneKernel.IDENTITY);
            }
            return out;
        }
        throw new IllegalArgumentException("`spec` must be a kernel name or a named map");
    }

    /** Map tokens through a table; null/absent tokens pass through (R NA semantics). */
    private static String[] mapTokens(String[] endpoints, Map<String, String> table) {
        String[] result = new String[endpoints.length];
        for (int i = 0; i < endpoints.length; i++) {
            String t = endpoints[i];
            result[i] = (t == null) ? null : table.getOrDefault(t, t);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> toStringMap(Map<?, ?> m) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return out;
    }
}
