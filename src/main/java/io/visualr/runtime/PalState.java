package io.visualr.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PAL state object — Java mirror of the R {@code visualr_pal} S3 object
 * (Layer 1, see {@code pal_state.R} / {@code format_pal.R}).
 *
 * <p>Fields align with the R constructor {@code new_pal_state}:</p>
 * <ul>
 *   <li>{@code shells} — character vector of shell tokens (any non-negative length)</li>
 *   <li>{@code core}   — single character scalar (the singularity token)</li>
 *   <li>{@code mapping_pack_id} — versioned mapping-pack string</li>
 *   <li>{@code provenance} — named list; atomic values only (Integer/Double/Boolean/String)</li>
 * </ul>
 *
 * <p>Token domain is closed at construction time, mirroring R's
 * {@code validate_pal}: newline, unit-separator and empty tokens are
 * rejected BEFORE they can reach serialization.</p>
 *
 * <p>R remains the authoritative semantics; this class is the transport
 * mirror, not a semantic fork.</p>
 */
public final class PalState {

    /** R constant DEFAULT_MAPPING_PACK_ID (verified: pal-jiugong-v0.2). */
    public static final String DEFAULT_MAPPING_PACK_ID = "pal-jiugong-v0.2";

    /** Unit separator used by the v0.2 length-prefixed format. */
    public static final char UNIT_SEP = '\u001f';

    private final List<String> shells;
    private final String core;
    private final String mappingPackId;
    private final Map<String, Object> provenance;

    private PalState(List<String> shells, String core, String mappingPackId,
                     Map<String, Object> provenance) {
        this.shells = Collections.unmodifiableList(shells);
        this.core = core;
        this.mappingPackId = mappingPackId;
        this.provenance = Collections.unmodifiableMap(new LinkedHashMap<>(provenance));
    }

    /** Constructor with invariant checks (mirror of {@code new_pal_state}). */
    public static PalState of(List<String> shells, String core,
                              String mappingPackId, Map<String, Object> provenance) {
        Objects.requireNonNull(shells, "shells");
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(mappingPackId, "mappingPackId");
        Objects.requireNonNull(provenance, "provenance");

        if (core.isEmpty()) {
            throw new IllegalArgumentException("core must not be empty");
        }
        if (core.indexOf('\n') >= 0 || core.indexOf(UNIT_SEP) >= 0) {
            throw new IllegalArgumentException("core must not contain newline or unit separator");
        }
        for (String s : shells) {
            if (s == null || s.isEmpty()) {
                throw new IllegalArgumentException("shell tokens must be non-empty");
            }
            if (s.indexOf('\n') >= 0 || s.indexOf(UNIT_SEP) >= 0) {
                throw new IllegalArgumentException("shell tokens must not contain newline or unit separator");
            }
        }
        if (mappingPackId.indexOf('\n') >= 0 || mappingPackId.indexOf(UNIT_SEP) >= 0) {
            throw new IllegalArgumentException("mappingPackId must not contain newline or unit separator");
        }
        for (Map.Entry<String, Object> e : provenance.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k == null || k.isEmpty()) {
                throw new IllegalArgumentException("provenance keys must be non-empty");
            }
            if (k.indexOf('|') >= 0 || k.indexOf('=') >= 0) {
                // '=' would make the key=value record ambiguous (gate review P2-5)
                throw new IllegalArgumentException("provenance keys must not contain '|' or '='");
            }
            if (!(v instanceof Integer || v instanceof Double || v instanceof Boolean
                    || v instanceof String)) {
                throw new IllegalArgumentException(
                        "provenance values must be atomic (Integer/Double/Boolean/String)");
            }
            if (v instanceof String s && (s.indexOf('|') >= 0 || s.indexOf('\n') >= 0)) {
                // '|' breaks the idx|key record; '\n' breaks the line framing
                throw new IllegalArgumentException(
                        "provenance string values must not contain '|' or newline");
            }
        }
        return new PalState(shells, core, mappingPackId, provenance);
    }

    public List<String> shells() { return shells; }

    public String core() { return core; }

    public String mappingPackId() { return mappingPackId; }

    public Map<String, Object> provenance() { return provenance; }

    @Override
    public String toString() {
        return String.format("<visualr_pal> shells=%d core=%s mapping_pack=%s",
                shells.size(), core, mappingPackId);
    }
}
