package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Nested palindrome codec — MUST satisfy PAL_NESTED_CONTRACT test vectors
 * (inst/PAL_NESTED_CONTRACT.md §7) and invariants (§6).
 */
class NestedPalCodecTest {

    @Test
    void contractTestVectors() {
        assertEquals(List.of("A"), NestedPalCodec.parse("{A}"));
        assertEquals(List.of("A", "B", "A"), NestedPalCodec.parse("{A{B}A}"));
        assertEquals(List.of("A", "B", "C", "B", "A"), NestedPalCodec.parse("{A{B{C}B}A}"));
        // multi-character symbol rule (Python reference bug, fixed 2026-08-07)
        assertEquals(List.of("AB", "C", "AB"), NestedPalCodec.parse("{AB{C}AB}"));
        // canonical S_4
        assertEquals(List.of("A", "B", "C", "D", "e", "D", "C", "B", "A"),
                NestedPalCodec.parse("{A{B{C{D{e}D}C}B}A}"));
    }

    @Test
    void encodeContractVectors() {
        assertEquals("{A}", NestedPalCodec.encode(List.of("A")));
        assertEquals("{A{B}A}", NestedPalCodec.encode(List.of("A", "B", "A")));
        assertEquals("{AB{C}AB}", NestedPalCodec.encode(List.of("AB", "C", "AB")));
        assertEquals("{A{B{C{D{e}D}C}B}A}",
                NestedPalCodec.encode(List.of("A", "B", "C", "D", "e", "D", "C", "B", "A")));
    }

    @Test
    void roundTripInvariant() {
        String[] texts = {"{A}", "{A{B}A}", "{A{B{C}B}A}", "{AB{C}AB}",
                "{A{B{C{D{e}D}C}B}A}"};
        for (String t : texts) {
            List<String> path = NestedPalCodec.parse(t);
            assertEquals(t, NestedPalCodec.encode(path),
                    "encode(parse(text)) == text for " + t);
            assertEquals(path, NestedPalCodec.parse(NestedPalCodec.encode(path)),
                    "parse(encode(path)) == path for " + t);
        }
    }

    @Test
    void utf8MultiByteSymbols() {
        // code-point safe: Chinese tokens (multi-byte UTF-8)
        assertEquals(List.of("甲", "乙", "甲"), NestedPalCodec.parse("{甲{乙}甲}"));
        assertEquals("{甲{乙}甲}", NestedPalCodec.encode(List.of("甲", "乙", "甲")));
    }

    @Test
    void rejectsMalformed() {
        assertThrows(IllegalArgumentException.class, () -> NestedPalCodec.parse("A{B}A"));   // no outer braces
        assertThrows(IllegalArgumentException.class, () -> NestedPalCodec.parse("{A{B}A"));   // trailing junk
        assertThrows(IllegalArgumentException.class, () -> NestedPalCodec.parse("{A{A}B}"));  // asymmetric close
        assertThrows(IllegalArgumentException.class, () -> NestedPalCodec.parse("{}"));       // empty symbol
        assertThrows(IllegalArgumentException.class, () -> NestedPalCodec.parse("{A{B}C}"));  // mismatch
    }

    @Test
    void rejectsInvalidPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> NestedPalCodec.encode(List.of("A", "B")));          // even length
        assertThrows(IllegalArgumentException.class,
                () -> NestedPalCodec.encode(List.of("A", "B", "C")));     // asymmetric
        assertThrows(IllegalArgumentException.class,
                () -> NestedPalCodec.encode(List.of("A{", "B", "A{")));   // braces in symbol
    }

    @Test
    void depthCap() {
        // 65 shells (depth 65) exceeds MAX_SHELLS=64
        java.util.List<String> deep = new java.util.ArrayList<>();
        for (int i = 0; i < 65; i++) {
            deep.add("S" + i);
        }
        deep.add("core");
        for (int i = 64; i >= 0; i--) {
            deep.add("S" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> NestedPalCodec.encode(deep));
    }

    @Test
    void palRoundTripViaNested() {
        PalState p = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, java.util.Map.of());
        String nested = NestedPalCodec.encodePal(p);
        assertEquals("{A{B{C{D{e}D}C}B}A}", nested);
        PalState back = NestedPalCodec.parsePal(nested);
        assertEquals(p.shells(), back.shells());
        assertEquals(p.core(), back.core());
    }
}
