package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pure-Java round-trip invariants for the PAL v0.2 codec.
 *
 * <p>Invariant 1 (R): {@code parse_pal(format_pal(S)) == S}.
 * Also verifies token-domain closure and type fidelity.</p>
 */
class PalCodecTest {

    private static PalState sample(List<String> shells, String core) {
        return PalState.of(shells, core, PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    @Test
    void roundTripFourShells() {
        PalState p = sample(List.of("A", "B", "C", "D"), "e");
        String s = PalCodec.format(p);
        PalState back = PalCodec.parse(s);
        assertEquals(p.shells(), back.shells());
        assertEquals(p.core(), back.core());
        assertEquals(p.mappingPackId(), back.mappingPackId());
        assertTrue(back.provenance().isEmpty());
    }

    @Test
    void roundTripEmptyShells() {
        PalState p = sample(List.of(), "e");
        String s = PalCodec.format(p);
        assertEquals("visualr_pal/v0.2\nshells:0\ncore:e\nmapping_pack_id:pal-jiugong-v0.2\nprovenance:0|", s);
        PalState back = PalCodec.parse(s);
        assertTrue(back.shells().isEmpty());
        assertEquals("e", back.core());
    }

    @Test
    void provenanceTypeFidelity() {
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("clock", 42);          // Integer
        prov.put("rate", 1.5);          // Double
        prov.put("flag", true);         // Boolean -> l:TRUE
        prov.put("note", "x");          // String
        PalState p = PalState.of(List.of("A"), "e", PalState.DEFAULT_MAPPING_PACK_ID, prov);
        String s = PalCodec.format(p);
        assertEquals(
                "visualr_pal/v0.2\nshells:1\u001fA\ncore:e\nmapping_pack_id:pal-jiugong-v0.2\n"
                        + "provenance:0|clock=i:42\nprovenance:1|rate=d:1.5\n"
                        + "provenance:2|flag=l:TRUE\nprovenance:3|note=c:x",
                s);
        PalState back = PalCodec.parse(s);
        assertEquals(42, back.provenance().get("clock"));
        assertEquals(1.5, back.provenance().get("rate"));
        assertEquals(true, back.provenance().get("flag"));
        assertEquals("x", back.provenance().get("note"));
        // exact string round-trip
        assertEquals(s, PalCodec.format(back));
    }

    @Test
    void tokenDomainClosure() {
        assertThrows(IllegalArgumentException.class,
                () -> sample(List.of("A\nB"), "e"));               // newline in shell
        assertThrows(IllegalArgumentException.class,
                () -> sample(List.of("A"), "e\nx"));               // newline in core
        assertThrows(IllegalArgumentException.class,
                () -> sample(List.of("A", ""), "e"));              // empty shell token
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("k|v", 1);                                         // '|' in key
        assertThrows(IllegalArgumentException.class,
                () -> PalState.of(List.of("A"), "e", PalState.DEFAULT_MAPPING_PACK_ID, bad));
    }

    @Test
    void parseRejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> PalCodec.parse("garbage"));
        assertThrows(IllegalArgumentException.class,
                () -> PalCodec.parse("visualr_pal/v0.2\nshells:2\u001fA\ncore:e\nmapping_pack_id:pal-jiugong-v0.2\nprovenance:0|"));
    }

    @Test
    void deeperShellsRoundTrip() {
        PalState p = sample(List.of("A", "B", "C", "D", "E"), "F");
        String s = PalCodec.format(p);
        PalState back = PalCodec.parse(s);
        assertEquals(List.of("A", "B", "C", "D", "E"), back.shells());
        assertEquals("F", back.core());
    }
}
