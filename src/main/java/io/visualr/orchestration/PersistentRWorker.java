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
 * Persistent R worker — a long-lived Rscript subprocess that executes
 * the R reference {@code run_topology_pipeline} repeatedly over a
 * request/response loop (avoids the ~3s process-start cost per task).
 *
 * <p>Protocol (dependency-free, line-counted; identical framing to
 * {@link RWorker} but looped):</p>
 *
 * <pre>
 * request:  kernelName \n  N \n  pal_line_1 \n ... pal_line_N
 * response: N \n  pal_line_1 \n ... pal_line_N        (N &gt;= 0, ok)
 *           -1 \n  error_message                        (task error; worker lives on)
 * quit:     __QUIT__ \n
 * </pre>
 *
 * <p>Task-level errors are caught inside the worker and reported with a
 * {@code -1} line count so the process survives and keeps serving.</p>
 *
 * <p>Not thread-safe by itself: one {@code PersistentRWorker} serves one
 * task at a time (use a pool for concurrency).</p>
 */
public final class PersistentRWorker implements AutoCloseable {

    /** R package root; override via env visualR_R_PACKAGE. */
    public static final String R_PACKAGE =
            System.getenv().getOrDefault("visualR_R_PACKAGE", "/mnt/d/visualR/visualR");

    static final String QUIT = "__QUIT__";

    static final String WORKER_SCRIPT =
            "suppressMessages(pkgload::load_all(\"" + R_PACKAGE + "\"))\n"
            + "con <- file(\"stdin\", open=\"r\")\n"
            + "repeat {\n"
            + "  kernel <- readLines(con, n=1, warn=FALSE)\n"
            + "  if (length(kernel) == 0 || identical(kernel, \"" + QUIT + "\")) break\n"
            + "  n <- as.integer(readLines(con, n=1, warn=FALSE))\n"
            + "  pal_lines <- readLines(con, n=n, warn=FALSE)\n"
            + "  pal <- parse_pal(paste(pal_lines, collapse=\"\\n\"))\n"
            + "  kernels_arg <- if (identical(kernel, \"identity\")) NULL else kernel\n"
            + "  res <- tryCatch(run_topology_pipeline(pal, kernels_arg), error=function(e) e)\n"
            + "  if (inherits(res, \"error\")) {\n"
            + "    cat(\"-1\\n\", sep=\"\")\n"
            + "    cat(conditionMessage(res), sep=\"\\n\")\n"
            + "  } else {\n"
            + "    out_lines <- strsplit(format_pal(res$pal_out), \"\\n\", fixed=TRUE)[[1]]\n"
            + "    cat(length(out_lines), \"\\n\", sep=\"\")\n"
            + "    cat(out_lines, sep=\"\\n\")\n"
            + "  }\n"
            + "  flush(stdout())\n"
            + "}\n";

    private final Process proc;
    private final BufferedWriter stdin;
    private final BufferedReader stdout;
    private final BufferedReader stderr;
    private volatile boolean closed = false;

    /** Start one long-lived worker. */
    public PersistentRWorker() throws IOException {
        proc = new ProcessBuilder("Rscript", "-e", WORKER_SCRIPT)
                .redirectErrorStream(false)
                .start();
        stdin = new BufferedWriter(
                new OutputStreamWriter(proc.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
        stderr = new BufferedReader(
                new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8));
    }

    /**
     * Run one pipeline task on this worker (synchronized: one task at a
     * time per worker process).
     */
    public synchronized PalState runTask(PalState pal, String kernelName) {
        if (closed) {
            throw new IllegalStateException("worker is closed");
        }
        List<String> palLines = List.of(PalCodec.format(pal).split("\n", -1));
        try {
            stdin.write(kernelName == null ? "identity" : kernelName);
            stdin.newLine();
            stdin.write(String.valueOf(palLines.size()));
            stdin.newLine();
            for (String line : palLines) {
                stdin.write(line);
                stdin.newLine();
            }
            stdin.flush();

            String countLine = stdout.readLine();
            if (countLine == null) {
                throw new IllegalStateException("R worker closed unexpectedly");
            }
            int n;
            try {
                n = Integer.parseInt(countLine.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException(
                        "R worker protocol error: expected line count, got '" + countLine + "'");
            }
            if (n == -1) {
                StringBuilder errMsg = new StringBuilder();
                String e;
                while ((e = stdout.readLine()) != null && !e.isEmpty()) {
                    errMsg.append(e).append('\n');
                }
                throw new IllegalStateException("R worker task error: " + errMsg);
            }
            List<String> outLines = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String line = stdout.readLine();
                if (line == null) {
                    throw new IllegalStateException("R worker truncated response at line " + i);
                }
                outLines.add(line);
            }
            return PalCodec.parse(String.join("\n", outLines));
        } catch (IOException ex) {
            throw new IllegalStateException("R worker I/O failure: " + ex.getMessage(), ex);
        }
    }

    /** True if the underlying process is still alive. */
    public boolean isAlive() {
        return proc.isAlive();
    }

    /** Drain any stderr (useful for diagnostics after a failure). */
    public String drainStderr() {
        StringBuilder sb = new StringBuilder();
        try {
            String line;
            while ((line = stderr.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException ignored) {
            // best effort
        }
        return sb.toString();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            stdin.write(QUIT);
            stdin.newLine();
            stdin.flush();
        } catch (IOException ignored) {
            // process may already be gone
        }
        try {
            proc.waitFor();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        proc.destroy();
    }
}
