package com.foodloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRankerTest {

    private static final String KEY = "test-key";
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 17, 12, 0);

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseBody = "{}";
    private volatile long delayMs = 0;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/recommend/ngos", ex -> {
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

    private AiRanker ranker(String key, long readMs) {
        return new AiRanker(mapper, "http://127.0.0.1:" + server.getAddress().getPort(), key, 500, readMs);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private RuleBasedMatcher.DonationInfo info() {
        return new RuleBasedMatcher.DonationInfo(36.0, 11.001, 76.975, now,
                now.plusHours(1), now.plusHours(3), now.plusHours(8));
    }

    private List<RuleBasedMatcher.Candidate> candidates() {
        return List.of(
                new RuleBasedMatcher.Candidate(6, 10.999, 77.03, 150.0, 40.0, LocalTime.of(8, 0), LocalTime.of(21, 0)),
                new RuleBasedMatcher.Candidate(7, 11.05, 76.99, 20.0, null, LocalTime.of(8, 0), LocalTime.of(21, 0)),
                new RuleBasedMatcher.Candidate(8, 11.02, 76.97, 90.0, null, null, null));
    }

    private List<RuleBasedMatcher.Result> eligible() {
        return List.of(
                new RuleBasedMatcher.Result(6, 1, bd("80.00"), bd("5.00"), Map.of("distance", bd("20"))),
                new RuleBasedMatcher.Result(7, 2, bd("60.00"), bd("6.00"), Map.of("distance", bd("18"))),
                new RuleBasedMatcher.Result(8, 3, bd("50.00"), bd("7.00"), Map.of("distance", bd("17"))));
    }

    private static List<Long> ids(List<RuleBasedMatcher.Result> results) {
        return results.stream().map(RuleBasedMatcher.Result::ngoId).toList();
    }

    private List<RuleBasedMatcher.Result> run(AiRanker r, int topN) {
        return r.rerank(4L, "Cooked Meals", info(), eligible(), candidates(), topN);
    }

    @Test
    void disabledWithoutApiKeyAndServerIsNotCalled() {
        AiRanker r = ranker("", 1000);
        assertFalse(r.isEnabled());
        assertEquals(List.of(6L, 7L, 8L), ids(run(r, 5)));
        assertNull(lastBody.get());
    }

    @Test
    void aiOrderAndScoresAreUsedAndMissingOnesAreFilledFromRules() {
        responseBody = "{\"recommendations\":[{\"rank\":1,\"ngoId\":7,\"matchScore\":91.5,\"distanceKm\":6.0},"
                + "{\"rank\":2,\"ngoId\":6,\"matchScore\":70.25,\"distanceKm\":5.0}]}";

        List<RuleBasedMatcher.Result> out = run(ranker(KEY, 2000), 5);

        assertEquals(List.of(7L, 6L, 8L), ids(out));
        assertEquals(1, out.get(0).rank());
        assertEquals(0, bd("91.50").compareTo(out.get(0).score()));
        assertEquals(0, bd("70.25").compareTo(out.get(1).score()));
        assertEquals(3, out.get(2).rank());
        assertEquals(0, bd("50.00").compareTo(out.get(2).score()));
    }

    @Test
    void ngoIdsThatWereNotEligibleAreIgnored() {
        responseBody = "{\"recommendations\":[{\"ngoId\":99,\"matchScore\":99},{\"ngoId\":6,\"matchScore\":88}]}";

        assertEquals(List.of(6L, 7L, 8L), ids(run(ranker(KEY, 2000), 5)));
    }

    @Test
    void scoreIsClampedToZeroToHundred() {
        responseBody = "{\"recommendations\":[{\"ngoId\":6,\"matchScore\":250}]}";

        List<RuleBasedMatcher.Result> out = run(ranker(KEY, 2000), 5);

        assertEquals(0, bd("100").compareTo(out.get(0).score()));
    }

    @Test
    void topNIsRespected() {
        responseBody = "{\"recommendations\":[{\"ngoId\":8,\"matchScore\":90},{\"ngoId\":7,\"matchScore\":80},{\"ngoId\":6,\"matchScore\":70}]}";

        assertEquals(List.of(8L, 7L), ids(run(ranker(KEY, 2000), 2)));
    }

    @Test
    void requestCarriesTheKeyAndTheEligibleCandidates() throws Exception {
        responseBody = "{\"recommendations\":[]}";

        run(ranker(KEY, 2000), 5);

        assertEquals(KEY, lastKey.get());
        JsonNode body = mapper.readTree(lastBody.get());
        assertEquals("Cooked Meals", body.path("donation").path("category").asText());
        assertEquals(36.0, body.path("donation").path("quantityKg").asDouble());
        assertEquals("2026-11-17T20:00:00", body.path("donation").path("expiresAt").asText());
        assertEquals(5, body.path("topN").asInt());
        assertEquals(3, body.path("candidates").size());
        assertEquals("Cooked Meals", body.path("candidates").get(0).path("acceptedCategories").get(0).asText());
        assertEquals("08:00", body.path("candidates").get(0).path("availableFrom").asText());
        assertTrue(body.path("candidates").get(1).path("openRequirementKg").isMissingNode());
    }

    @Test
    void httpErrorFallsBackToRules() {
        status = 500;
        responseBody = "{\"error\":\"boom\"}";

        assertEquals(List.of(6L, 7L, 8L), ids(run(ranker(KEY, 2000), 5)));
    }

    @Test
    void unauthorisedFallsBackToRules() {
        status = 401;

        assertEquals(List.of(6L, 7L, 8L), ids(run(ranker(KEY, 2000), 5)));
    }

    @Test
    void malformedJsonFallsBackToRules() {
        responseBody = "this is not json";

        assertEquals(List.of(6L, 7L, 8L), ids(run(ranker(KEY, 2000), 5)));
    }

    @Test
    void missingRecommendationsFieldFallsBackToRules() {
        responseBody = "{}";

        assertEquals(List.of(6L, 7L, 8L), ids(run(ranker(KEY, 2000), 5)));
    }

    @Test
    void unreachableServerFallsBackToRules() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        AiRanker r = new AiRanker(mapper, "http://127.0.0.1:" + closedPort, KEY, 300, 1000);

        assertEquals(List.of(6L, 7L, 8L), ids(run(r, 5)));
    }

    @Test
    void slowServerFallsBackToRules() {
        delayMs = 1500;
        responseBody = "{\"recommendations\":[{\"ngoId\":8,\"matchScore\":90}]}";

        assertEquals(List.of(6L, 7L, 8L), ids(run(ranker(KEY, 300), 5)));
    }
}