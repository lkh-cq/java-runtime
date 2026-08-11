package io.visualr.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping pack registry — mirror of R {@code .visualR_pack_registry} +
 * {@code register_mapping_pack} / {@code resolve_mapping_pack}
 * (mapping_pack.R). Resolution is FAIL-CLOSED: unknown ids error and
 * list the registered ids; the frozen default pack is registered here
 * at class load, mirroring R's {@code .onLoad}.
 */
public final class MappingPackRegistry {

    private static final Map<String, MappingPack> REGISTRY = new LinkedHashMap<>();

    static {
        register(defaultPack(), false);
    }

    private MappingPackRegistry() {}

    /** Register a pack; duplicate id fails closed unless overwrite. */
    public static void register(MappingPack pack, boolean overwrite) {
        if (pack == null || pack.id() == null) {
            throw new IllegalArgumentException("pack must have an id");
        }
        if (!overwrite && REGISTRY.containsKey(pack.id())) {
            throw new IllegalArgumentException(
                    "Mapping pack '" + pack.id() + "' already registered (fail closed). Use overwrite=true");
        }
        REGISTRY.put(pack.id(), pack);
    }

    /** Resolve a pack by id; unknown id fails closed (mirror of R). */
    public static MappingPack resolve(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("`id` must be a single non-empty string");
        }
        MappingPack pack = REGISTRY.get(id);
        if (pack == null) {
            throw new IllegalArgumentException("Unknown mapping pack '" + id
                    + "' (fail closed). Registered: " + String.join(", ", sortedIds()));
        }
        return pack;
    }

    /** Sorted registered pack ids. */
    public static List<String> sortedIds() {
        List<String> ids = new ArrayList<>(REGISTRY.keySet());
        ids.sort(String::compareTo);
        return ids;
    }

    /** Frozen default pack — mirror of the R .onLoad pal-jiugong-v0.2 registration. */
    private static MappingPack defaultPack() {
        Map<String, OrbitEntry> orbitTable = new LinkedHashMap<>();
        orbitTable.put("A", new OrbitEntry(1, 9, new int[] {1, 1}, new int[] {3, 3}));
        orbitTable.put("B", new OrbitEntry(2, 8, new int[] {1, 2}, new int[] {3, 2}));
        orbitTable.put("C", new OrbitEntry(3, 7, new int[] {1, 3}, new int[] {3, 1}));
        orbitTable.put("D", new OrbitEntry(4, 6, new int[] {2, 1}, new int[] {2, 3}));
        orbitTable.put("e", new OrbitEntry(5, 5, new int[] {2, 2}, new int[] {2, 2}));

        return MappingPack.of(
                "pal-jiugong-v0.2",
                orbitTable,
                List.of("e", "D", "C", "B", "A"),   // EXPAND_ORDER (center-outward)
                Map.of(),                            // complement_table (empty default)
                List.of("A", "B", "C", "D", "e"),    // frozen_symbols
                "carrier_11x11",                     // carrier_fn (R reference)
                null,                                // gamma_rule
                "tolower",                           // local_center_transform
                Map.of(),                            // closure_policy
                "0.2.2",
                "Frozen pal-jiugong mapping pack (sanyuan-runtime v0.2)");
    }
}
