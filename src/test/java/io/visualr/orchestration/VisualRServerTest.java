package io.visualr.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.visualr.runtime.PalCodec;
import io.visualr.runtime.PalState;

/**
 * HTTP transport layer: health + pipeline over the JDK HttpServer.
 */
class VisualRServerTest {

    private static VisualRServer server;
    private static HttpClient client;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        server = new VisualRServer(0); // ephemeral port
        base = "http://127.0.0.1:" + server.port();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stop() {
        server.close();
    }

    private static PalState s4() {
        return PalState.of(List.of("A", "B", "C", "D"), "e",
                PalState.DEFAULT_MAPPING_PACK_ID, Map.of());
    }

    private static String encodeBody(PalState pal, String kernel) {
        List<String> palLines = List.of(PalCodec.format(pal).split("\n", -1));
        StringBuilder sb = new StringBuilder();
        sb.append(kernel == null ? "identity" : kernel).append('\n');
        sb.append(palLines.size()).append('\n');
        for (String l : palLines) {
            sb.append(l).append('\n');
        }
        return sb.toString();
    }

    @Test
    void health() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/health")).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertEquals("ok", res.body());
    }

    @Test
    void pipelineIdentity() throws Exception {
        HttpResponse<String> res = post("/pipeline", encodeBody(s4(), "identity"));
        assertEquals(200, res.statusCode(), "body=" + res.body());
        PalState out = parseResponse(res.body());
        assertEquals(PalCodec.format(s4()), PalCodec.format(out));
    }

    @Test
    void pipelineRotate() throws Exception {
        HttpResponse<String> res = post("/pipeline", encodeBody(s4(), "rotate"));
        assertEquals(200, res.statusCode());
        PalState out = parseResponse(res.body());
        assertEquals(List.of("B", "C", "D", "A"), out.shells());
    }

    @Test
    void malformedRequestRejected() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/pipeline"))
                .POST(HttpRequest.BodyPublishers.ofString("garbage"))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
        assertTrue(res.body().startsWith("-1\n"));
    }

    @Test
    void concurrentRequests() throws Exception {
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final String kernel = (i % 2 == 0) ? "identity" : "rotate";
            futures.add(client.sendAsync(
                    HttpRequest.newBuilder(URI.create(base + "/pipeline"))
                            .POST(HttpRequest.BodyPublishers.ofString(encodeBody(s4(), kernel)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString()));
        }
        for (int i = 0; i < futures.size(); i++) {
            HttpResponse<String> res = futures.get(i).join();
            assertEquals(200, res.statusCode(), "request " + i);
            PalState out = parseResponse(res.body());
            if (i % 2 == 0) {
                assertEquals(PalCodec.format(s4()), PalCodec.format(out));
            } else {
                assertEquals(List.of("B", "C", "D", "A"), out.shells());
            }
        }
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static PalState parseResponse(String body) {
        String[] lines = body.split("\n", -1);
        int n = Integer.parseInt(lines[0].trim());
        List<String> palLines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            palLines.add(lines[i + 1]);
        }
        return PalCodec.parse(String.join("\n", palLines));
    }
}
