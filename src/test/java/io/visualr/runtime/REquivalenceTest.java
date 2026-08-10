package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * R-equivalence tests: the Java PAL codec MUST byte-match the R reference
 * implementation (R remains the authoritative semantics; Java is a mirror,
 * never a semantic fork). Verified via {@code Rscript} + {@code pkgload}.
 *
 * <p>This is an equivalence gate, not a fallback: if Rscript/pkgload is
 * missing or the strings differ, the test FAILS (no silent degradation).</p>
 */
class REquivalenceTest {

    private static final String R_PACKAGE = "/mnt/d/visualR/visualR";

    private static final String SEP = "@@RS@@";

    /** Run R, return stdout. */
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

    private static final String R_SCRIPT =
            "suppressMessages(pkgload::load_all(\"" + R_PACKAGE + "\"))\n"
            + "p1 <- new_pal_state(c(\"A\",\"B\",\"C\",\"D\"), \"e\")\n"
            + "p2 <- new_pal_state(character(0), \"e\")\n"
            + "p3 <- new_pal_state(c(\"A\"), \"e\", provenance=list(clock=42L, rate=1.5, flag=TRUE, note=\"x\"))\n"
            + "p4 <- new_pal_state(c(\"A\",\"B\",\"C\",\"D\",\"E\"), \"F\")\n"
            + "out <- c(format_pal(p1), format_pal(p2), format_pal(p3), format_pal(p4))\n"
            + "cat(paste(out, collapse=\"" + SEP + "\"))\n";

    private static PalState sample(List<String> shells, String core) {
        return PalState.of(shells, core, PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static PalState sampleWithProvenance() {
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("clock", 42);
        prov.put("rate", 1.5);
        prov.put("flag", true);
        prov.put("note", "x");
        return PalState.of(List.of("A"), "e", PalState.DEFAULT_MAPPING_PACK_ID, prov);
    }

    @Test
    void javaFormatByteMatchesR() {
        String rOut = runR(R_SCRIPT).trim();
        String[] rSamples = rOut.split(java.util.regex.Pattern.quote(SEP));

        String[] javaSamples = {
                PalCodec.format(sample(List.of("A", "B", "C", "D"), "e")),
                PalCodec.format(sample(List.of(), "e")),
                PalCodec.format(sampleWithProvenance()),
                PalCodec.format(sample(List.of("A", "B", "C", "D", "E"), "F"))
        };

        assertEquals(4, rSamples.length, "R must emit 4 samples");
        for (int i = 0; i < 4; i++) {
            String r = rSamples[i].trim();
            String j = javaSamples[i].trim();
            assertEquals(r, j, "sample " + (i + 1) + " must byte-match R format_pal");
        }
    }

    @Test
    void javaParseOfRFormatThenReformatMatches() {
        String rOut = runR(R_SCRIPT).trim();
        String[] rSamples = rOut.split(java.util.regex.Pattern.quote(SEP));
        for (int i = 0; i < rSamples.length; i++) {
            String r = rSamples[i].trim();
            PalState parsed = PalCodec.parse(r);
            assertNotNull(parsed, "sample " + (i + 1) + " must parse");
            assertEquals(r, PalCodec.format(parsed).trim(),
                    "sample " + (i + 1) + " reformat must equal R format");
        }
    }
}
