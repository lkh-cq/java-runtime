package io.visualr.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping pack — mirror of the R {@code visualr_mapping_pack} object
 * ({@code new_mapping_pack}, mapping_pack.R). The pack is the authority
 * for PAL/jiugong expansion rules; resolve is FAIL-CLOSED (unknown ids
 * error, never a silent fallback).
 */
public final class MappingPack {

    private final String id;
    private final Map<String, OrbitEntry> orbitTable;
    private final List<String> expandOrder;
    private final Map<String, String> complementTable;
    private final List<String> frozenSymbols;
    private final String carrierFn;            // R carrier function name (reference only)
    private final String gammaRule;            // null in the frozen default pack
    private final String localCenterTransform; // e.g. "tolower", or null
    private final Map<String, Object> closurePolicy;
    private final String version;
    private final String description;

    private MappingPack(String id, Map<String, OrbitEntry> orbitTable,
                        List<String> expandOrder, Map<String, String> complementTable,
                        List<String> frozenSymbols, String carrierFn, String gammaRule,
                        String localCenterTransform, Map<String, Object> closurePolicy,
                        String version, String description) {
        this.id = id;
        this.orbitTable = Collections.unmodifiableMap(new LinkedHashMap<>(orbitTable));
        this.expandOrder = Collections.unmodifiableList(expandOrder);
        this.complementTable = Collections.unmodifiableMap(new LinkedHashMap<>(complementTable));
        this.frozenSymbols = Collections.unmodifiableList(frozenSymbols);
        this.carrierFn = carrierFn;
        this.gammaRule = gammaRule;
        this.localCenterTransform = localCenterTransform;
        this.closurePolicy = Collections.unmodifiableMap(new LinkedHashMap<>(closurePolicy));
        this.version = version;
        this.description = description;
    }

    /** Constructor with validation (mirror of R {@code new_mapping_pack}). */
    public static MappingPack of(String id, Map<String, OrbitEntry> orbitTable,
                                 List<String> expandOrder, Map<String, String> complementTable,
                                 List<String> frozenSymbols, String carrierFn, String gammaRule,
                                 String localCenterTransform, Map<String, Object> closurePolicy,
                                 String version, String description) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("`id` must be a single non-empty string");
        }
        if (orbitTable == null || orbitTable.isEmpty()) {
            throw new IllegalArgumentException("`orbit_table` must be a non-empty map");
        }
        if (expandOrder == null || expandOrder.isEmpty()) {
            throw new IllegalArgumentException("`expand_order` must be a non-empty list");
        }
        if (frozenSymbols == null || frozenSymbols.isEmpty()) {
            throw new IllegalArgumentException("`frozen_symbols` must be a non-empty list");
        }
        return new MappingPack(id, orbitTable, expandOrder, complementTable, frozenSymbols,
                carrierFn, gammaRule, localCenterTransform, closurePolicy, version, description);
    }

    public String id() { return id; }

    public Map<String, OrbitEntry> orbitTable() { return orbitTable; }

    public List<String> expandOrder() { return expandOrder; }

    public Map<String, String> complementTable() { return complementTable; }

    public List<String> frozenSymbols() { return frozenSymbols; }

    public String carrierFn() { return carrierFn; }

    public String gammaRule() { return gammaRule; }

    public String localCenterTransform() { return localCenterTransform; }

    public Map<String, Object> closurePolicy() { return closurePolicy; }

    public String version() { return version; }

    public String description() { return description; }
}
