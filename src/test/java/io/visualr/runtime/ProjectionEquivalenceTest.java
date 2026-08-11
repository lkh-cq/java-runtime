package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Projection equivalence: Java jiugong/square-view projection must
 * byte-match R {@code pal_to_jiugong} / {@code pal_to_square_view}.
 */
class ProjectionEquivalenceTest {

    private static final String R_PACKAGE = "/mnt/d/visualR/visualR";

    private static final String SEP = "@@RS@@";

    private static String runR(String script) {
        try {
            ProcessBuilder pb = new ProcessBuilder("Rscript", "-e", script);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            int code = proc.waitFor();
            if (code != 0) {
                fail("Rscript exited " + code + ":\n" + out);
            }
            return out.toString();
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("Rscript unavailable: " + e.getMessage(), e);
        }
    }

    @Test
    void jiugongGridByteMatchesR() {
        String rOut = runR(
                "suppressMessages(pkgload::load_all(\"" + R_PACKAGE + "\"))\n"
                + "p <- new_pal_state(c(\"A\",\"B\",\"C\",\"D\"), \"e\")\n"
                + "jg <- pal_to_jiugong(p)\n"
                + "cat(paste(jg$grid, collapse=\",\"))\n").trim();

        PalState p = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        String[][] grid = PalProjection.palToJiugong(p);
        // R's paste(matrix) iterates COLUMN-major (matrix storage), so
        // flatten the Java row-major grid column-first to compare bytes.
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) {
                if (c > 0 || r > 0) sb.append(',');
                sb.append(grid[r][c]);
            }
        }
        assertEquals(rOut, sb.toString(), "3x3 jiugong grid must byte-match R");
    }

    @Test
    void mirrorAddrMatchesR() {
        String rOut = runR(
                "suppressMessages(pkgload::load_all(\"" + R_PACKAGE + "\"))\n"
                + "out <- vapply(1:3, function(r) vapply(1:3, function(c) "
                + "paste(mirror_addr(r, c), collapse=\",\"), character(1)), character(3))\n"
                + "cat(paste(out, collapse=\";\"))\n").trim();

        String[] expected = rOut.split(";");
        int idx = 0;
        for (int r = 1; r <= 3; r++) {
            for (int c = 1; c <= 3; c++) {
                int[] m = PalProjection.mirrorAddr(r, c);
                assertEquals(expected[idx].trim(), m[0] + "," + m[1],
                        "mirror_addr(" + r + "," + c + ")");
                idx++;
            }
        }
        assertArrayEquals(new int[] {3, 3}, PalProjection.mirrorAddr(1, 1));
    }
}
