package com.foodloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPredictionClientTest {

    private static final String KEY = "test-key";
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{\"ok\":true}";
    private volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            lastPath.set(ex.getRequestURI().getPath());
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastKey.set(ex.getRequestHeaders().getFirst("X-Internal-Api-Key"));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private AiPredictionClient client(String key, long readMs) {
        return new AiPredictionClient(mapper, "http://127.0.0.1:" + server.getAddress().getPort() + "/", key, 500, readMs);
    }

    private JsonNode body() {
        return mapper.createObjectNode().put("category", "Cooked Meals");
    }

    @Test
    void notConfiguredIsUnavailableAndServerIsNotCalled() {
        AiPredictionClient c = client("", 1000);
        assertThrows(AiPredictionClient.AiUnavailableException.class, () -> c.post("/api/v1/predict/surplus", body()));
        assertEquals(0, hits.get());
    }

    @Test
    void postSendsKeyPathAndBodyAndReturnsJson() throws Exception {
        responseBody = "{\"predictedSurplusKg\":23.8}";

        AiPredictionClient.Call call = client(KEY, 2000).post("/api/v1/predict/surplus", body());

        assertEquals(23.8, call.body().path("predictedSurplusKg").asDouble());
        assertEquals(KEY, lastKey.get());
        assertEquals("/api/v1/predict/surplus", lastPath.get());
        assertEquals("Cooked Meals", mapper.readTree(lastBody.get()).path("category").asText());
        assertTrue(call.latencyMs() >= 0);
    }

    @Test
    void getWorksToo() {
        responseBody = "{\"status\":\"UP\"}";

        assertEquals("UP", client(KEY, 2000).get("/health").body().path("status").asText());
        assertEquals("/health", lastPath.get());
    }

    @Test
    void serverErrorIsUnavailable() {
        status = 500;

        assertThrows(AiPredictionClient.AiUnavailableException.class, () -> client(KEY, 2000).post("/x", body()));
    }

    @Test
    void unauthorisedIsUnavailable() {
        status = 401;

        assertThrows(AiPredictionClient.AiUnavailableException.class, () -> client(KEY, 2000).post("/x", body()));
    }

    @Test
    void badRequestIsRejectedWithTheServiceMessage() {
        status = 400;
        responseBody = "{\"error\":{\"code\":\"VALIDATION_ERROR\",\"message\":\"Invalid input\",\"details\":[{\"field\":\"area\"}]}}";

        AiPredictionClient.AiRejectedException e = assertThrows(AiPredictionClient.AiRejectedException.class,
                () -> client(KEY, 2000).post("/x", body()));

        assertTrue(e.getMessage().contains("Invalid input"));
        assertTrue(e.getMessage().contains("area"));
    }

    @Test
    void malformedJsonIsUnavailable() {
        responseBody = "this is not json";

        assertThrows(AiPredictionClient.AiUnavailableException.class, () -> client(KEY, 2000).post("/x", body()));
    }

    @Test
    void unreachableServerIsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        AiPredictionClient c = new AiPredictionClient(mapper, "http://127.0.0.1:" + closedPort, KEY, 300, 1000);

        assertThrows(AiPredictionClient.AiUnavailableException.class, () -> c.post("/x", body()));
    }

    @Test
    void slowServerIsUnavailable() {
        delayMs = 1500;

        assertThrows(AiPredictionClient.AiUnavailableException.class, () -> client(KEY, 300).post("/x", body()));
    }

    @Test
    void circuitOpensAfterThreeFailuresInARow() {
        status = 500;
        AiPredictionClient c = client(KEY, 2000);
        for (int i = 0; i < 3; i++) {
            assertThrows(AiPredictionClient.AiUnavailableException.class, () -> c.post("/x", body()));
        }

        AiPredictionClient.AiUnavailableException e = assertThrows(AiPredictionClient.AiUnavailableException.class,
                () -> c.post("/x", body()));

        assertTrue(e.getMessage().contains("circuit"));
        assertEquals(3, hits.get());
    }

    @Test
    void aSuccessResetsTheFailureCount() {
        AiPredictionClient c = client(KEY, 2000);
        status = 500;
        for (int i = 0; i < 2; i++) {
            assertThrows(AiPredictionClient.AiUnavailableException.class, () -> c.post("/x", body()));
        }
        status = 200;
        c.post("/x", body());
        status = 500;
        for (int i = 0; i < 2; i++) {
            assertThrows(AiPredictionClient.AiUnavailableException.class, () -> c.post("/x", body()));
        }
        status = 200;

        c.post("/x", body());

        assertEquals(6, hits.get());
    }

    @Test
    void getCallsNeverTripTheCircuit() {
        status = 500;
        AiPredictionClient c = client(KEY, 2000);
        for (int i = 0; i < 5; i++) {
            assertThrows(AiPredictionClient.AiUnavailableException.class, () -> c.get("/health"));
        }

        assertEquals(5, hits.get());
    }
}