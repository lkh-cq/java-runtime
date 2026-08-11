package io.visualr.orchestration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;
import io.visualr.runtime.PipelineResult;
import io.visualr.runtime.TopologyOperator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VisualR HTTP service — long-lived network transport for pipeline tasks
 * (DEVELOPMENT_PLAN §8: network transport role). Built on the JDK's
 * {@code com.sun.net.httpserver} — zero external dependencies.
 *
 * <p>Endpoints:</p>
 * <pre>
 * GET  /health    -&gt; "ok"
 * POST /pipeline  -&gt; request/response in the same line-counted PAL
 *                    framing as the R worker protocol:
 *                    body:   kernelName \n N \n pal_line_1 \n ... pal_line_N
 *                    reply:  N \n pal_line_1 \n ... pal_line_N   (result PAL)
 *                    or:     -1 \n error message
 * </pre>
 *
 * <p>The service executes the Java in-process pipeline (byte-equivalent
 * to the R reference); R workers remain available for out-of-process
 * execution via {@link Orchestrator}.</p>
 */
public final class VisualRServer implements AutoCloseable {

    private final HttpServer server;
    private final ExecutorService pool;
    private final int requestedPort;

    /** Start the service on the given port (binds immediately; 0 = ephemeral). */
    public VisualRServer(int port) throws IOException {
        this.requestedPort = port;
        this.pool = Executors.newFixedThreadPool(4);
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", this::handleHealth);
        server.createContext("/pipeline", this::handlePipeline);
        server.setExecutor(pool);
        server.start();
    }

    /** Actual bound port (differs from the requested port when 0/ephemeral). */
    public int port() { return server.getAddress().getPort(); }

    private void handleHealth(HttpExchange ex) throws IOException {
        byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private void handlePipeline(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }
        try {
            Request req = readRequest(ex.getRequestBody());
            PipelineResult res = TopologyOperator.runPipeline(req.pal(), req.kernel());
            String outFormat = PalCodec.format(res.palOut());
            byte[] body = encodeResponse(outFormat);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        } catch (IllegalArgumentException ex2) {
            String detail = (ex2.getMessage() == null) ? ex2.getClass().getName() : ex2.getMessage();
            byte[] body = ("-1\n" + detail).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(400, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static Request readRequest(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            lines.add(line);
        }
        if (lines.size() < 2) {
            throw new IllegalArgumentException("malformed request: expected kernel + count");
        }
        String kernel = lines.get(0);
        int n;
        try {
            n = Integer.parseInt(lines.get(1).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("malformed request: bad line count");
        }
        if (lines.size() != 2 + n) {
            throw new IllegalArgumentException("malformed request: line count mismatch");
        }
        PalState pal = PalCodec.parse(String.join("\n", lines.subList(2, 2 + n)));
        return new Request(pal, "identity".equals(kernel) ? null : kernel);
    }

    private static byte[] encodeResponse(String palFormat) {
        String[] lines = palFormat.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        sb.append(lines.length).append('\n');
        for (String l : lines) {
            sb.append(l).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private record Request(PalState pal, String kernel) {}

    @Override
    public void close() {
        server.stop(0);
        pool.shutdown();
    }
}
