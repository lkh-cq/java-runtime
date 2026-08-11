package io.visualr.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Full-pipeline R equivalence: the Java Topology Operator pipeline
 * (PAL -&gt; carrier -&gt; snapshot -&gt; lanes -&gt; barrier -&gt;
 * reconcile -&gt; commit -&gt; PAL re-encoding) must byte-match the R
 * reference {@code run_topology_pipeline} on the re-encoded PAL, and
 * must agree on reconcile action/phase.
 *
 * <p>Equivalence gate: FAILS loudly when Rscript/pkgload is missing or
 * outputs differ (no silent degradation).</p>
 */
class PipelineEquivalenceTest {

    /** R package root; override via env visualR_R_PACKAGE (CI clones elsewhere). */
    private static final String R_PACKAGE =
            System.getenv().getOrDefault("visualR_R_PACKAGE", "/mnt/d/visualR/visualR");

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
            // strip only the trailing newline the reader added (never trim content)
            return out.toString().replaceFirst("\n$", "");
        } catch (IOException | InterruptedException e) {
            throw new AssertionError("Rscript unavailable: " + e.getMessage(), e);
        }
    }

    private static final String R_PIPELINE =
            "suppressMessages(pkgload::load_all(\"" + R_PACKAGE + "\"))\n"
            + "p1 <- new_pal_state(c(\"A\",\"B\",\"C\",\"D\"), \"e\")\n"
            + "p2 <- new_pal_state(c(\"A\",\"B\",\"C\",\"D\",\"E\"), \"F\")\n"
            + "p3 <- new_pal_state(c(\"A\",\"B\",\"C\",\"D\"), \"e\")\n"
            + "res1 <- run_topology_pipeline(p1)\n"
            + "res2 <- run_topology_pipeline(p2)\n"
            + "res3 <- run_topology_pipeline(p3, \"rotate\")\n"
            + "out <- c(paste(res1$reconciled$action, res1$reconciled$phase, sep=\"|\"),\n"
            + "         format_pal(res1$pal_out),\n"
            + "         paste(res2$reconciled$action, res2$reconciled$phase, sep=\"|\"),\n"
            + "         format_pal(res2$pal_out),\n"
            + "         paste(res3$reconciled$action, res3$reconciled$phase, sep=\"|\"),\n"
            + "         format_pal(res3$pal_out))\n"
            + "cat(paste(out, collapse=\"" + SEP + "\"))\n";

    @Test
    void pipelineReencodedPalByteMatchesR() {
        String rOut = runR(R_PIPELINE);
        String[] rSamples = rOut.split(java.util.regex.Pattern.quote(SEP));
        assertEquals(6, rSamples.length, "R must emit 6 samples");

        // S4 identity pipeline
        PalState p1 = PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        PipelineResult j1 = TopologyOperator.runPipeline(p1);
        assertEquals(rSamples[0], "promote|idle", "R S4 reconcile");
        assertEquals("promote", j1.reconciled().action());
        assertEquals("idle", j1.reconciled().phase());
        assertEquals(rSamples[1], PalCodec.format(j1.palOut()),
                "S4 re-encoded PAL must byte-match R");

        // S5 identity pipeline (dynamic lanes A/B/C/D/E + e — execute_lanes_ops
        // semantics; every orbit gets a lane, no shell dropped)
        PalState p2 = PalState.of(List.of("A", "B", "C", "D", "E"), "F",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
        PipelineResult j2 = TopologyOperator.runPipeline(p2);
        assertEquals(rSamples[2], "promote|idle", "R S5 reconcile");
        assertEquals(rSamples[3], PalCodec.format(j2.palOut()),
                "S5 re-encoded PAL must byte-match R");
        // dynamic lanes keep all five shells (Branch-1 audit 2026-08-09)
        assertEquals(5, j2.palOut().shells().size(),
                "S5 dynamic-lane pipeline keeps every shell");

        // rotate kernel pipeline: S4 with kernel-name spec
        PipelineResult j3 = TopologyOperator.runPipeline(p1, "rotate");
        assertEquals(rSamples[4], "promote|idle", "R rotate reconcile");
        assertEquals(rSamples[5], PalCodec.format(j3.palOut()),
                "rotate re-encoded PAL must byte-match R");
        // rotate advances each shell token one step on the A->B->C->D->A cycle
        assertEquals(List.of("B", "C", "D", "A"), j3.palOut().shells());
    }
}
