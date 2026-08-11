package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Mapping pack registry + default pack + projections
 * (mirror of R mapping_pack.R / complement.R / jiugong.R).
 */
class MappingPackTest {

    @Test
    void defaultPackRegistered() {
        MappingPack pack = MappingPackRegistry.resolve("pal-jiugong-v0.2");
        assertEquals("0.2.2", pack.version());
        assertEquals(List.of("e", "D", "C", "B", "A"), pack.expandOrder());
        assertEquals(List.of("A", "B", "C", "D", "e"), pack.frozenSymbols());
        assertEquals("tolower", pack.localCenterTransform());
    }

    @Test
    void orbitTableFrozenValues() {
        MappingPack pack = MappingPackRegistry.resolve("pal-jiugong-v0.2");
        OrbitEntry a = pack.orbitTable().get("A");
        assertEquals(1, a.head());
        assertEquals(9, a.tail());
        assertArrayEquals(new int[] {1, 1}, a.addr1());
        assertArrayEquals(new int[] {3, 3}, a.addr2());

        OrbitEntry e = pack.orbitTable().get("e");
        assertEquals(5, e.head());
        assertEquals(5, e.tail());
        assertArrayEquals(new int[] {2, 2}, e.addr1());
        assertArrayEquals(new int[] {2, 2}, e.addr2());
    }

    @Test
    void unknownPackFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> MappingPackRegistry.resolve("nonexistent-pack"));
    }

    @Test
    void registerDuplicateFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> MappingPackRegistry.register(MappingPackRegistry.resolve("pal-jiugong-v0.2"), false));
    }

    @Test
    void mirrorAddrCentralInversion() {
        assertArrayEquals(new int[] {3, 3}, PalProjection.mirrorAddr(1, 1));
        assertArrayEquals(new int[] {1, 1}, PalProjection.mirrorAddr(3, 3));
        // center is a fixed point; Sigma^2 = I
        assertArrayEquals(new int[] {2, 2}, PalProjection.mirrorAddr(2, 2));
        assertArrayEquals(new int[] {1, 2}, PalProjection.mirrorAddr(3, 2));
        assertThrows(IllegalArgumentException.class, () -> PalProjection.mirrorAddr(0, 1));
        assertThrows(IllegalArgumentException.class, () -> PalProjection.mirrorAddr(4, 1));
    }

    @Test
    void palToJiugongS4Grid() {
        PalState p = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        String[][] grid = PalProjection.palToJiugong(p);
        // unfolded = A B C D e D C B A, row-major 3x3
        String[][] expected = {
                {"A", "B", "C"},
                {"D", "e", "D"},
                {"C", "B", "A"}
        };
        for (int r = 0; r < 3; r++) {
            assertArrayEquals(expected[r], grid[r]);
        }
    }

    @Test
    void palToJiugongRejectsNonS4() {
        PalState p = PalState.of(List.of("A", "B", "C", "D", "E"), "F",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        // unfolded length 11 -> not 9 -> error with guidance
        assertThrows(IllegalArgumentException.class, () -> PalProjection.palToJiugong(p));
    }

    @Test
    void palToSquareViewGeneral() {
        PalState s0 = PalState.of(List.of(), "e", PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        String[][] g1 = PalProjection.palToSquareView(s0);
        assertArrayEquals(new String[][] {{"e"}}, g1);

        PalState s4 = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        String[][] g3 = PalProjection.palToSquareView(s4);
        assertEquals(3, g3.length);

        assertThrows(IllegalArgumentException.class,
                () -> PalProjection.palToSquareView(
                        PalState.of(List.of("A", "B", "C"), "e",
                                PalState.DEFAULT_MAPPING_PACK_ID, Map.of())));
    }

    @Test
    void carrierAutoProjectionS4() {
        PalState p = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        TopologyCarrier carrier = TopologyCarrier.fromPal(p);
        assertTrue(carrier.projection() != null, "S4 carrier auto-materializes 3x3 projection");
        assertEquals("e", carrier.projection()[1][1]);
        // non-S4 (S5) carrier: projection null
        TopologyCarrier c5 = TopologyCarrier.fromPal(
                PalState.of(List.of("A", "B", "C", "D", "E"), "F",
                        PalState.DEFAULT_MAPPING_PACK_ID, Map.of()));
        assertTrue(c5.projection() == null);
    }

    @Test
    void resolvePackFromPal() {
        PalState p = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        MappingPack pack = TopologyOperator.palResolvePack(p);
        assertEquals("pal-jiugong-v0.2", pack.id());
    }
}
