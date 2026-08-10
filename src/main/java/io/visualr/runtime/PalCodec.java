package io.visualr.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PAL Layer-1 codec — Java mirror of {@code format_pal} / {@code parse_pal}
 * (R v0.2 format, hardened 2026-08-06 against RCE).
 *
 * <p>Serialization format (one record per line, no eval, no
 * {@code parse(text=)}):</p>
 * <pre>
 * visualr_pal/v0.2
 * shells:4\u001fA\u001fB\u001fC\u001fD     (count \u001f v1 \u001f ... \u001f vN)
 * core:E
 * mapping_pack_id:pal-jiugong-v0.1
 * provenance:0|                       (empty)
 * provenance:1|clock=i:42             (idx | key = TYPE:value)
 * </pre>
 *
 * <p>Type prefixes preserve lossless round-trip ({@code 42L != "42"}):
 * {@code i}=Integer, {@code d}=Double, {@code l}=Boolean, {@code c}=String.</p>
 *
 * <p>Invariant 1 (R): {@code parse_pal(format_pal(S)) == S}.</p>
 */
public final class PalCodec {

    public static final String FORMAT_HEADER = "visualr_pal/v0.2";

    private PalCodec() {}

    /** Serialize a PAL state to the v0.2 record format (no trailing newline, mirroring R). */
    public static String format(PalState pal) {
        java.util.StringJoiner sj = new java.util.StringJoiner("\n");
        sj.add(FORMAT_HEADER);

        int n = pal.shells().size();
        if (n == 0) {
            sj.add("shells:0");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("shells:").append(n);
            for (String s : pal.shells()) {
                sb.append(PalState.UNIT_SEP).append(s);
            }
            sj.add(sb.toString());
        }

        sj.add("core:" + pal.core());
        sj.add("mapping_pack_id:" + pal.mappingPackId());

        if (pal.provenance().isEmpty()) {
            sj.add("provenance:0|");
        } else {
            int i = 0;
            for (Map.Entry<String, Object> e : pal.provenance().entrySet()) {
                sj.add("provenance:" + i + "|" + e.getKey() + "=" + encodeType(e.getValue()));
                i++;
            }
        }
        return sj.toString();
    }

    private static String encodeType(Object v) {
        if (v instanceof Integer i) {
            return "i:" + i;
        } else if (v instanceof Double d) {
            return "d:" + d;
        } else if (v instanceof Boolean b) {
            // R serializes logicals as l:TRUE / l:FALSE (uppercase).
            return "l:" + (b ? "TRUE" : "FALSE");
        } else {
            return "c:" + v; // String
        }
    }

    /** Parse a v0.2 record string back into a PAL state. */
    public static PalState parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("null PAL text");
        }
        String[] lines = text.split("\n", -1);
        int idx = 0;
        if (idx >= lines.length || !lines[idx].equals(FORMAT_HEADER)) {
            throw new IllegalArgumentException("missing format header, got: " + (idx < lines.length ? lines[idx] : "<eof>"));
        }
        idx++;

        List<String> shells = new ArrayList<>();
        String core = null;
        String mappingPackId = null;
        Map<String, Object> provenance = new LinkedHashMap<>();

        for (; idx < lines.length; idx++) {
            String line = lines[idx];
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("shells:")) {
                String rest = line.substring("shells:".length());
                if (rest.equals("0")) {
                    shells.clear();
                } else {
                    int sep = rest.indexOf(PalState.UNIT_SEP);
                    if (sep < 0) {
                        throw new IllegalArgumentException("shells record without unit separator: " + line);
                    }
                    int count = Integer.parseInt(rest.substring(0, sep));
                    String[] toks = rest.substring(sep + 1).split(String.valueOf(PalState.UNIT_SEP), -1);
                    if (toks.length != count) {
                        throw new IllegalArgumentException("shells count mismatch: declared " + count + " got " + toks.length);
                    }
                    shells.clear();
                    for (String t : toks) {
                        shells.add(t);
                    }
                }
            } else if (line.startsWith("core:")) {
                core = line.substring("core:".length());
            } else if (line.startsWith("mapping_pack_id:")) {
                mappingPackId = line.substring("mapping_pack_id:".length());
            } else if (line.startsWith("provenance:")) {
                String rest = line.substring("provenance:".length());
                int bar = rest.indexOf('|');
                if (bar < 0) {
                    throw new IllegalArgumentException("provenance record without '|': " + line);
                }
                // rest after '|' may be empty (empty provenance marker)
                String kv = rest.substring(bar + 1);
                if (kv.isEmpty()) {
                    continue; // provenance:0|
                }
                int eq = kv.indexOf('=');
                if (eq < 0) {
                    throw new IllegalArgumentException("provenance record without '=': " + line);
                }
                String key = kv.substring(0, eq);
                String enc = kv.substring(eq + 1);
                provenance.put(key, decodeType(enc));
            } else {
                throw new IllegalArgumentException("unknown record: " + line);
            }
        }

        if (core == null) {
            throw new IllegalArgumentException("missing core record");
        }
        if (mappingPackId == null) {
            mappingPackId = PalState.DEFAULT_MAPPING_PACK_ID;
        }
        return PalState.of(shells, core, mappingPackId, provenance);
    }

    private static Object decodeType(String enc) {
        int colon = enc.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("provenance value without type prefix: " + enc);
        }
        String type = enc.substring(0, colon);
        String val = enc.substring(colon + 1);
        return switch (type) {
            case "i" -> Integer.valueOf(val);
            case "d" -> Double.valueOf(val);
            case "l" -> Boolean.valueOf(val);
            case "c" -> val;
            default -> throw new IllegalArgumentException("unknown provenance type prefix: " + type);
        };
    }
}
