package com.foodloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Re-ranks the NGOs that already passed the rule-based hard filters using the Flask AI service.
 * Any problem (no key, timeout, HTTP error, bad JSON) falls back to the rule-based order.
 */
@Component
public class AiRanker {

    private static final Logger log = LoggerFactory.getLogger(AiRanker.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ObjectMapper mapper;
    private final String endpoint;
    private final String apiKey;
    private final Duration readTimeout;
    private final HttpClient http;

    public AiRanker(ObjectMapper mapper,
                    @Value("${ai.service.base-url:http://localhost:5000}") String baseUrl,
                    @Value("${ai.service.api-key:}") String apiKey,
                    @Value("${ai.service.connect-timeout-ms:1000}") long connectTimeoutMs,
                    @Value("${ai.service.read-timeout-ms:3000}") long readTimeoutMs) {
        this.mapper = mapper;
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/api/v1/recommend/ngos";
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.readTimeout = Duration.ofMillis(readTimeoutMs);
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /**
     * @param eligible   NGOs that passed every hard rule, best rule-based score first
     * @param candidates the matcher inputs, used to build the AI request
     * @return at most topN results; the rule-based top N when the AI service cannot be used
     */
    public List<RuleBasedMatcher.Result> rerank(Long donationId, String category,
                                                RuleBasedMatcher.DonationInfo info,
                                                List<RuleBasedMatcher.Result> eligible,
                                                List<RuleBasedMatcher.Candidate> candidates,
                                                int topN) {
        List<RuleBasedMatcher.Result> fallback = new ArrayList<>(eligible.subList(0, Math.min(topN, eligible.size())));
        if (!isEnabled() || eligible.isEmpty()) {
            return fallback;
        }
        try {
            String body = mapper.writeValueAsString(buildRequest(donationId, category, info, eligible, candidates, topN));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Api-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("AI service answered HTTP " + response.statusCode());
            }
            JsonNode recommendations = mapper.readTree(response.body()).path("recommendations");
            if (!recommendations.isArray()) {
                throw new IllegalStateException("AI response has no recommendations array");
            }
            return merge(recommendations, eligible, topN);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI ranking interrupted, using rule-based ranking");
            return fallback;
        } catch (Exception e) {
            log.warn("AI service unavailable, using rule-based ranking: {}", e.toString());
            return fallback;
        }
    }

    private ObjectNode buildRequest(Long donationId, String category, RuleBasedMatcher.DonationInfo info,
                                    List<RuleBasedMatcher.Result> eligible,
                                    List<RuleBasedMatcher.Candidate> candidates, int topN) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode donation = root.putObject("donation");
        if (donationId != null) {
            donation.put("donationId", donationId);
        }
        donation.put("category", category);
        donation.put("quantityKg", info.quantityKg());
        donation.put("latitude", info.latitude());
        donation.put("longitude", info.longitude());
        donation.put("expiresAt", info.expiresAt().format(DATE_TIME));
        donation.put("hoursToExpiry", Duration.between(info.now(), info.expiresAt()).toMinutes() / 60.0);

        Set<Long> ids = new HashSet<>();
        for (RuleBasedMatcher.Result r : eligible) {
            ids.add(r.ngoId());
        }
        ArrayNode list = root.putArray("candidates");
        for (RuleBasedMatcher.Candidate c : candidates) {
            if (!ids.contains(c.ngoId())) {
                continue;
            }
            ObjectNode n = list.addObject();
            n.put("ngoId", c.ngoId());
            n.put("latitude", c.latitude());
            n.put("longitude", c.longitude());
            n.put("remainingCapacityKg", c.remainingCapacityKg());
            n.putArray("acceptedCategories").add(category);
            if (c.openRequirementKg() != null) {
                n.put("openRequirementKg", c.openRequirementKg());
            }
            if (c.availableFrom() != null) {
                n.put("availableFrom", c.availableFrom().format(TIME));
            }
            if (c.availableTo() != null) {
                n.put("availableTo", c.availableTo().format(TIME));
            }
        }
        root.put("topN", topN);
        return root;
    }

    private List<RuleBasedMatcher.Result> merge(JsonNode recommendations,
                                                List<RuleBasedMatcher.Result> eligible, int topN) {
        Map<Long, RuleBasedMatcher.Result> byId = new LinkedHashMap<>();
        for (RuleBasedMatcher.Result r : eligible) {
            byId.put(r.ngoId(), r);
        }
        List<RuleBasedMatcher.Result> out = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (JsonNode n : recommendations) {
            if (out.size() >= topN) {
                break;
            }
            long id = n.path("ngoId").asLong(-1);
            RuleBasedMatcher.Result base = byId.get(id);
            if (base == null || !used.add(id)) {
                continue;
            }
            BigDecimal score = base.score();
            if (n.hasNonNull("matchScore") && n.get("matchScore").isNumber()) {
                score = n.get("matchScore").decimalValue().setScale(2, RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO).min(HUNDRED);
            }
            out.add(new RuleBasedMatcher.Result(id, out.size() + 1, score, base.distanceKm(), base.breakdown()));
        }
        for (RuleBasedMatcher.Result r : eligible) {
            if (out.size() >= topN) {
                break;
            }
            if (used.add(r.ngoId())) {
                out.add(new RuleBasedMatcher.Result(r.ngoId(), out.size() + 1, r.score(), r.distanceKm(), r.breakdown()));
            }
        }
        return out;
    }
}