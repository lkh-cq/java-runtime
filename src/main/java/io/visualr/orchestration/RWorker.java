package io.visualr.orchestration;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * R worker process — one Rscript subprocess that executes the R reference
 * {@code run_topology_pipeline} and returns the re-encoded PAL.
 *
 * <p>Interaction model (DEVELOPMENT_PLAN §8): Java/JVM orchestrates,
 * R workers own PAL semantics. This is a process encapsulation with a
 * dependency-free line protocol over stdin/stdout:</p>
 *
 * <pre>
 * request:  kernelName \n  N \n  pal_line_1 \n ... pal_line_N
 * response: N \n  pal_line_1 \n ... pal_line_N
 * </pre>
 *
 * <p>The PAL format string may contain newlines (record format), so the
 * line count is transmitted explicitly. No eval, no shared interpreter —
 * each worker is a fresh R process (R remains authoritative).</p>
 *
 * <p>Worker script uses {@code pkgload::load_all} on the visualR package;
 * the package root is configurable ({@code visualR_R_PACKAGE} env var).</p>
 */
public final class RWorker {

    /** R package root; override via env visualR_R_PACKAGE. */
    public static final String R_PACKAGE =
            System.getenv().getOrDefault("visualR_R_PACKAGE", "/mnt/d/visualR/visualR");

    static final String WORKER_SCRIPT =
            "suppressMessages(pkgload::load_all(\"" + R_PACKAGE + "\"))\n"
            + "con <- file(\"stdin\", open=\"r\")\n"
            + "kernel <- readLines(con, n=1, warn=FALSE)\n"
            + "n <- as.integer(readLines(con, n=1, warn=FALSE))\n"
            + "pal_lines <- readLines(con, n=n, warn=FALSE)\n"
            + "pal <- parse_pal(paste(pal_lines, collapse=\"\\n\"))\n"
            + "kernels_arg <- if (identical(kernel, \"identity\")) NULL else kernel\n"
            + "res <- run_topology_pipeline(pal, kernels_arg)\n"
            + "out_lines <- strsplit(format_pal(res$pal_out), \"\\n\", fixed=TRUE)[[1]]\n"
            + "cat(length(out_lines), \"\\n\", sep=\"\")\n"
            + "cat(out_lines, sep=\"\\n\")\n";

    private RWorker() {}

    /**
     * Run one pipeline task in a fresh R worker.
     *
     * @param pal        input PAL state
     * @param kernelName kernel name for all lanes; {@code "identity"} = default
     * @return the re-encoded S_(t+1) PAL state (R output, parsed)
     * @throws IllegalStateException on worker failure (stderr included)
     */
    public static PalState runTask(PalState pal, String kernelName) {
        List<String> palLines = List.of(PalCodec.format(pal).split("\n", -1));
        try {
            Process proc = new ProcessBuilder("Rscript", "-e", WORKER_SCRIPT)
                    .redirectErrorStream(false)
                    .start();

            try (BufferedWriter w = new BufferedWriter(
                    new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8))) {
                w.write(kernelName == null ? "identity" : kernelName);
                w.newLine();
                w.write(String.valueOf(palLines.size()));
                w.newLine();
                for (String line : palLines) {
                    w.write(line);
                    w.newLine();
                }
                w.flush();
            }

            BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
            BufferedReader stderr = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8));

            StringBuilder err = new StringBuilder();
            String e;
            while ((e = stderr.readLine()) != null) {
                err.append(e).append('\n');
            }

            String countLine = stdout.readLine();
            if (countLine == null) {
                throw new IllegalStateException("R worker produced no output. stderr:\n" + err);
            }
            int n;
            try {
                n = Integer.parseInt(countLine.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException(
                        "R worker protocol error: expected line count, got '" + countLine
                                + "'. stderr:\n" + err);
            }
            List<String> outLines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String line = stdout.readLine();
                if (line == null) {
                    throw new IllegalStateException("R worker truncated response at line " + i
                            + ". stderr:\n" + err);
                }
                outLines.add(line);
            }

            int code = proc.waitFor();
            if (code != 0) {
                throw new IllegalStateException("R worker exited " + code + ". stderr:\n" + err);
            }
            return PalCodec.parse(String.join("\n", outLines));
        } catch (IOException ex) {
            throw new IllegalStateException("R worker failed to start: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("R worker interrupted", ex);
        }
    }
}
