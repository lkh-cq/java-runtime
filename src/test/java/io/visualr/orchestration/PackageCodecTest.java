package io.visualr.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;

/**
 * Package codec: pack/unpack round-trip and fail-closed checksum
 * verification (DEVELOPMENT_PLAN §7).
 */
class PackageCodecTest {

    private static PalState s4() {
        return PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static PalState rotated() {
        return PalState.of(List.of("B", "C", "D", "A"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    @Test
    void packUnpackRoundTrip() {
        PalState input = s4();
        PalState result = rotated();
        String pkg = PackageCodec.pack(input, "rotate", result);
        PackageRecord rec = PackageCodec.unpack(pkg);

        assertEquals(PalCodec.format(input), rec.palFormat());
        assertEquals("pal-jiugong-v0.2", rec.mappingPackId());
        assertEquals("rotate", rec.kernel());
        assertEquals(PalCodec.format(result), rec.resultFormat());
        assertEquals(PackageCodec.checksum(PalCodec.format(input), "rotate", PalCodec.format(result)),
                rec.checksum());
    }

    @Test
    void identityKernelNormalized() {
        PalState p = s4();
        String pkg = PackageCodec.pack(p, null, p);
        PackageRecord rec = PackageCodec.unpack(pkg);
        assertEquals("identity", rec.kernel());
    }

    @Test
    void tamperedChecksumFailsClosed() {
        PalState input = s4();
        PalState result = rotated();
        String pkg = PackageCodec.pack(input, "rotate", result);
        // flip one char in the result region -> checksum must fail
        String tampered = pkg.replace("shells:4\u001fB", "shells:4\u001fX");
        assertThrows(IllegalArgumentException.class, () -> PackageCodec.unpack(tampered));
    }

    @Test
    void malformedPackageRejected() {
        assertThrows(IllegalArgumentException.class, () -> PackageCodec.unpack("garbage"));
        assertThrows(IllegalArgumentException.class, () -> PackageCodec.unpack(""));
        // truncated input must fail with IllegalArgumentException, not
        // ArrayIndexOutOfBoundsException (gate review P2-8)
        PalState input = s4();
        String pkg = PackageCodec.pack(input, "rotate", rotated());
        String truncated = pkg.split("\n", -1)[0] + "\nchecksum:abc\npal_lines:999";
        assertThrows(IllegalArgumentException.class, () -> PackageCodec.unpack(truncated));
    }
}
