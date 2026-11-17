package com.foodloop.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thin HTTP client for the Flask AI service (predict endpoints, health, models/info).
 * One retry on a connect error only. After three failed prediction calls in a row, calls are skipped
 * for 30 seconds (simple circuit breaker). A 400/422 answer means "the AI rejected the input" and
 * does not count as a failure of the service.
 */
@Component
public class AiPredictionClient {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionClient.class);
    static final int FAILURE_THRESHOLD = 3;
    static final long OPEN_MILLIS = 30_000;

    /** The AI service could not be used (no key, timeout, 5xx, bad JSON, circuit open). */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message) {
            super(message);
        }
    }

    /** The AI service answered 400/422: it understood the call but did not like the input. */
    public static class AiRejectedException extends RuntimeException {
        public AiRejectedException(String message) {
            super(message);
        }
    }

    public record Call(JsonNode body, int latencyMs) {
    }

    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final Duration readTimeout;
    private final HttpClient http;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile long openUntil = 0;

    public AiPredictionClient(ObjectMapper mapper,
                              @Value("${ai.service.base-url:http://localhost:5000}") String baseUrl,
                              @Value("${ai.service.api-key:}") String apiKey,
                              @Value("${ai.service.connect-timeout-ms:1000}") long connectTimeoutMs,
                              @Value("${ai.service.read-timeout-ms:3000}") long readTimeoutMs) {
        this.mapper = mapper;
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /** POST used for predictions. Counts towards the circuit breaker. */
    public Call post(String path, JsonNode body) {
        requireUsable(true);
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the AI request", e);
        }
        HttpRequest request = builder(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return execute(request, true);
    }

    /** GET used for health and model info. Never trips (or is blocked by) the circuit breaker. */
    public Call get(String path) {
        requireUsable(false);
        return execute(builder(path).GET().build(), false);
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(readTimeout)
                .header("X-Internal-Api-Key", apiKey);
    }

    private void requireUsable(boolean tracked) {
        if (!isConfigured()) {
            throw new AiUnavailableException("AI service API key is not configured");
        }
        if (tracked && System.currentTimeMillis() < openUntil) {
            throw new AiUnavailableException("AI service paused after repeated failures (circuit open)");
        }
    }

    private Call execute(HttpRequest request, boolean tracked) {
        long start = System.nanoTime();
        HttpResponse<String> response;
        try {
            response = send(request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("AI call was interrupted");
        } catch (IOException e) {
            throw failed(tracked, "AI service unreachable: " + e);
        }
        int ms = (int) ((System.nanoTime() - start) / 1_000_000);
        int code = response.statusCode();
        if (code == 200) {
            try {
                JsonNode json = mapper.readTree(response.body());
                if (json == null || json.isMissingNode()) {
                    throw new IOException("empty body");
                }
                if (tracked) {
                    failures.set(0);
                }
                return new Call(json, ms);
            } catch (IOException e) {
                throw failed(tracked, "AI service returned invalid JSON");
            }
        }
        if (code == 400 || code == 422) {
            if (tracked) {
                failures.set(0);
            }
            throw new AiRejectedException(rejectionMessage(response.body()));
        }
        throw failed(tracked, "AI service answered HTTP " + code);
    }

    /** One retry, and only when the connection itself could not be opened. */
    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException e) {
            log.info("AI connect failed, retrying once: {}", e.toString());
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private AiUnavailableException failed(boolean tracked, String message) {
        if (tracked && failures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openUntil = System.currentTimeMillis() + OPEN_MILLIS;
            log.warn("AI service failed {} times in a row, pausing calls for {} s",
                    FAILURE_THRESHOLD, OPEN_MILLIS / 1000);
        }
        log.warn("{}", message);
        return new AiUnavailableException(message);
    }

    private String rejectionMessage(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode err = root.has("error") && root.get("error").isObject() ? root.get("error") : root;
            String message = err.path("message").asText("AI service rejected the input");
            JsonNode details = err.path("details");
            return details.isMissingNode() || details.isNull() ? message : message + " " + details;
        } catch (Exception e) {
            return "AI service rejected the input";
        }
    }
}