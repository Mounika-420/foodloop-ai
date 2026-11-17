package com.foodloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodloop.dto.AiPredictionResponse;
import com.foodloop.dto.AiStatusResponse;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.FoodCategory;
import com.foodloop.entity.NgoProfile;
import com.foodloop.entity.Prediction;
import com.foodloop.entity.enums.DonorType;
import com.foodloop.entity.enums.PredictionType;
import com.foodloop.exception.BadRequestException;
import com.foodloop.exception.ResourceNotFoundException;
import com.foodloop.repository.DonorProfileRepository;
import com.foodloop.repository.FoodCategoryRepository;
import com.foodloop.repository.NgoProfileRepository;
import com.foodloop.repository.PredictionRepository;
import com.foodloop.repository.SurplusHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Surplus and demand predictions through the Flask AI service. Not transactional on purpose:
 * the HTTP call must never hold a database transaction open. Every call is stored in predictions.
 */
@Service
public class AiPredictionService {

    private static final Logger log = LoggerFactory.getLogger(AiPredictionService.class);
    static final int DEFAULT_DEMAND_HOUR = 18;

    private final AiPredictionClient client;
    private final DonorProfileRepository donors;
    private final NgoProfileRepository ngos;
    private final FoodCategoryRepository categories;
    private final PredictionRepository predictions;
    private final SurplusHistoryRepository history;
    private final ObjectMapper mapper;

    public AiPredictionService(AiPredictionClient client,
                               DonorProfileRepository donors,
                               NgoProfileRepository ngos,
                               FoodCategoryRepository categories,
                               PredictionRepository predictions,
                               SurplusHistoryRepository history,
                               ObjectMapper mapper) {
        this.client = client;
        this.donors = donors;
        this.ngos = ngos;
        this.categories = categories;
        this.predictions = predictions;
        this.history = history;
        this.mapper = mapper;
    }

    public AiPredictionResponse surplus(Long donorId, String categoryName, LocalDate date) {
        DonorProfile donor = donors.findById(donorId)
                .orElseThrow(() -> new ResourceNotFoundException("Donor profile not found"));
        FoodCategory category = findCategory(categoryName);
        LocalDate day = date == null ? LocalDate.now() : date;

        List<BigDecimal> last = history.latestQuantities(donorId, category.getId(), PageRequest.of(0, 1));
        double previous = last.isEmpty() || last.get(0) == null ? 0.0 : last.get(0).doubleValue();

        ObjectNode request = mapper.createObjectNode();
        request.put("category", category.getName());
        request.put("donorType", aiDonorType(donor.getDonorType()));
        request.put("dayOfWeek", dayCode(day));
        request.put("month", day.getMonthValue());
        request.put("eventFlag", false);
        request.put("previousQuantityKg", previous);
        if (donor.getLocation() != null && donor.getLocation().getArea() != null) {
            request.put("area", donor.getLocation().getArea());
        }
        return predict(PredictionType.SURPLUS, "/api/v1/predict/surplus", "predictedSurplusKg",
                request, donor, null, category);
    }

    public AiPredictionResponse demand(Long userId, String role, String categoryName, String area,
                                       LocalDate date, Integer hour) {
        if (area == null || area.isBlank()) {
            throw new BadRequestException("area is required");
        }
        int hourOfDay = hour == null ? DEFAULT_DEMAND_HOUR : hour;
        if (hourOfDay < 0 || hourOfDay > 23) {
            throw new BadRequestException("hour must be between 0 and 23");
        }
        FoodCategory category = findCategory(categoryName);
        LocalDate day = date == null ? LocalDate.now() : date;
        NgoProfile ngo = "NGO".equals(role) ? ngos.findById(userId).orElse(null) : null;

        ObjectNode request = mapper.createObjectNode();
        request.put("category", category.getName());
        request.put("area", area.trim());
        request.put("dayOfWeek", dayCode(day));
        request.put("month", day.getMonthValue());
        request.put("hourOfDay", hourOfDay);
        return predict(PredictionType.DEMAND, "/api/v1/predict/demand", "predictedDemandKg",
                request, null, ngo, category);
    }

    public AiStatusResponse status() {
        if (!client.isConfigured()) {
            return new AiStatusResponse(false, false, false, null, null, Map.of(), List.of(), null,
                    "AI_SERVICE_API_KEY is not set, so matching uses the rule-based ranking only");
        }
        try {
            AiPredictionClient.Call health = client.get("/health");
            JsonNode h = health.body();
            Map<String, Boolean> loaded = new LinkedHashMap<>();
            JsonNode ml = h.path("modelsLoaded");
            if (ml.isObject()) {
                ml.fields().forEachRemaining(e -> loaded.put(e.getKey(), e.getValue().asBoolean()));
            }
            boolean keyAccepted = false;
            List<Map<String, Object>> models = new ArrayList<>();
            try {
                AiPredictionClient.Call info = client.get("/api/v1/models/info");
                keyAccepted = true;
                for (JsonNode m : info.body().path("models")) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", m.path("name").asText(null));
                    row.put("algorithm", m.path("algorithm").asText(null));
                    row.put("mae", number(m.path("metrics").path("mae")));
                    row.put("r2", number(m.path("metrics").path("r2")));
                    models.add(row);
                }
            } catch (AiPredictionClient.AiUnavailableException | AiPredictionClient.AiRejectedException e) {
                log.warn("AI models/info not available: {}", e.getMessage());
            }
            String message = keyAccepted ? null : "AI service is up but did not accept the API key";
            return new AiStatusResponse(true, true, keyAccepted,
                    h.path("modelVersion").asText(null),
                    h.has("trainedOnSyntheticData") ? h.get("trainedOnSyntheticData").asBoolean() : null,
                    loaded, models, health.latencyMs(), message);
        } catch (AiPredictionClient.AiUnavailableException | AiPredictionClient.AiRejectedException e) {
            return new AiStatusResponse(true, false, false, null, null, Map.of(), List.of(), null, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ internals

    private AiPredictionResponse predict(PredictionType type, String path, String valueField, ObjectNode request,
                                         DonorProfile donor, NgoProfile ngo, FoodCategory category) {
        long start = System.nanoTime();
        JsonNode out = null;
        Integer latency = null;
        String message = null;
        try {
            AiPredictionClient.Call call = client.post(path, request);
            out = call.body();
            latency = call.latencyMs();
            if (!out.path(valueField).isNumber()) {
                message = "AI response had no " + valueField;
            }
        } catch (AiPredictionClient.AiUnavailableException | AiPredictionClient.AiRejectedException e) {
            message = e.getMessage();
        }
        if (latency == null) {
            latency = (int) ((System.nanoTime() - start) / 1_000_000);
        }
        boolean ok = out != null && out.path(valueField).isNumber();
        store(type, request, out, ok, message, latency, donor, ngo, category, valueField);

        if (!ok) {
            return new AiPredictionResponse(false, null, null, null, null, null, latency, message);
        }
        return new AiPredictionResponse(true,
                out.get(valueField).decimalValue().doubleValue(),
                out.path("modelName").asText(null),
                out.path("modelVersion").asText(null),
                out.path("trainedOnSyntheticData").asBoolean(true),
                out.path("confidenceNote").asText(null),
                latency, null);
    }

    private void store(PredictionType type, ObjectNode request, JsonNode out, boolean ok, String message,
                       Integer latency, DonorProfile donor, NgoProfile ngo, FoodCategory category,
                       String valueField) {
        try {
            Prediction p = new Prediction();
            p.setPredictionType(type);
            p.setDonor(donor);
            p.setNgo(ngo);
            p.setCategory(category);
            p.setInputJson(mapper.writeValueAsString(request));
            if (out != null) {
                p.setOutputJson(out.toString());
            } else {
                ObjectNode failure = mapper.createObjectNode();
                failure.put("aiAvailable", false);
                failure.put("message", message);
                p.setOutputJson(failure.toString());
            }
            p.setPredictedValue(ok ? out.get(valueField).decimalValue() : null);
            p.setModelName(ok ? out.path("modelName").asText("unknown") : "ai-unavailable");
            p.setModelVersion(ok ? out.path("modelVersion").asText("unknown") : "n/a");
            p.setTrainedOnSynthetic(!ok || out.path("trainedOnSyntheticData").asBoolean(true));
            p.setLatencyMs(latency);
            predictions.save(p);
        } catch (Exception e) {
            log.warn("Could not store the {} prediction: {}", type, e.toString());
        }
    }

    private FoodCategory findCategory(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("category is required");
        }
        return categories.findByName(name.trim())
                .orElseThrow(() -> new BadRequestException("Unknown food category: " + name));
    }

    static String dayCode(LocalDate day) {
        return day.getDayOfWeek().name().substring(0, 3);
    }

    /** The AI model knows HOTEL, RESTAURANT, BAKERY, SUPERMARKET and CATERER. */
    static String aiDonorType(DonorType type) {
        if (type == null) {
            return "RESTAURANT";
        }
        return switch (type) {
            case HOTEL -> "HOTEL";
            case RESTAURANT -> "RESTAURANT";
            case BAKERY -> "BAKERY";
            case SUPERMARKET -> "SUPERMARKET";
            case EVENT_ORGANIZER -> "CATERER";
            case OTHER -> "RESTAURANT";
        };
    }

    private static Double number(JsonNode n) {
        return n.isNumber() ? n.asDouble() : null;
    }
}