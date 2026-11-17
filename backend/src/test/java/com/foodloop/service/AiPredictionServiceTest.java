package com.foodloop.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodloop.dto.AiPredictionResponse;
import com.foodloop.dto.AiStatusResponse;
import com.foodloop.entity.DonorProfile;
import com.foodloop.entity.FoodCategory;
import com.foodloop.entity.Location;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiPredictionServiceTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 21);
    private static final String SURPLUS_JSON = "{\"predictedSurplusKg\":23.8,\"confidenceNote\":\"MAE 1.98 kg\","
            + "\"modelName\":\"GradientBoostingRegressor\",\"modelVersion\":\"1.0.0\",\"trainedOnSyntheticData\":true}";
    private static final String DEMAND_JSON = "{\"predictedDemandKg\":59.7,"
            + "\"modelName\":\"RandomForestRegressor\",\"modelVersion\":\"1.0.0\",\"trainedOnSyntheticData\":true}";

    @Mock AiPredictionClient client;
    @Mock DonorProfileRepository donors;
    @Mock NgoProfileRepository ngos;
    @Mock FoodCategoryRepository categories;
    @Mock PredictionRepository predictions;
    @Mock SurplusHistoryRepository history;

    private final ObjectMapper mapper = new ObjectMapper();
    private AiPredictionService service;
    private DonorProfile donor;
    private FoodCategory meals;

    @BeforeEach
    void setUp() {
        service = new AiPredictionService(client, donors, ngos, categories, predictions, history, mapper);
        Location loc = new Location();
        loc.setArea("RS Puram");
        donor = new DonorProfile();
        donor.setUserId(2L);
        donor.setDonorType(DonorType.HOTEL);
        donor.setLocation(loc);
        meals = new FoodCategory();
        meals.setId(1);
        meals.setName("Cooked Meals");
        when(donors.findById(2L)).thenReturn(Optional.of(donor));
        when(categories.findByName("Cooked Meals")).thenReturn(Optional.of(meals));
        when(history.latestQuantities(eq(2L), eq(1), any())).thenReturn(List.of(new BigDecimal("22.00")));
    }

    private AiPredictionClient.Call call(String json, int ms) throws Exception {
        return new AiPredictionClient.Call(mapper.readTree(json), ms);
    }

    private Prediction savedPrediction() {
        ArgumentCaptor<Prediction> captor = ArgumentCaptor.forClass(Prediction.class);
        verify(predictions).save(captor.capture());
        return captor.getValue();
    }

    private JsonNode sentRequest(String path) {
        ArgumentCaptor<JsonNode> captor = ArgumentCaptor.forClass(JsonNode.class);
        verify(client).post(eq(path), captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- surplus

    @Test
    void surplusReturnsThePredictionAndStoresIt() throws Exception {
        when(client.post(anyString(), any())).thenReturn(call(SURPLUS_JSON, 42));

        AiPredictionResponse r = service.surplus(2L, "Cooked Meals", SATURDAY);

        assertTrue(r.aiAvailable());
        assertEquals(23.8, r.predictedKg());
        assertEquals("GradientBoostingRegressor", r.modelName());
        assertEquals("1.0.0", r.modelVersion());
        assertEquals(42, r.latencyMs());
        assertNull(r.message());

        Prediction p = savedPrediction();
        assertEquals(PredictionType.SURPLUS, p.getPredictionType());
        assertSame(donor, p.getDonor());
        assertSame(meals, p.getCategory());
        assertNull(p.getNgo());
        assertEquals(0, new BigDecimal("23.8").compareTo(p.getPredictedValue()));
        assertEquals("GradientBoostingRegressor", p.getModelName());
        assertEquals(42, p.getLatencyMs());
        assertTrue(p.isTrainedOnSynthetic());
    }

    @Test
    void surplusRequestCarriesDonorHistoryAndDate() throws Exception {
        when(client.post(anyString(), any())).thenReturn(call(SURPLUS_JSON, 5));

        service.surplus(2L, "Cooked Meals", SATURDAY);

        JsonNode req = sentRequest("/api/v1/predict/surplus");
        assertEquals("Cooked Meals", req.path("category").asText());
        assertEquals("HOTEL", req.path("donorType").asText());
        assertEquals("SAT", req.path("dayOfWeek").asText());
        assertEquals(11, req.path("month").asInt());
        assertEquals(22.0, req.path("previousQuantityKg").asDouble());
        assertEquals("RS Puram", req.path("area").asText());
        assertFalse(req.path("eventFlag").asBoolean());
    }

    @Test
    void eventOrganiserIsSentAsCaterer() throws Exception {
        donor.setDonorType(DonorType.EVENT_ORGANIZER);
        when(client.post(anyString(), any())).thenReturn(call(SURPLUS_JSON, 5));

        service.surplus(2L, "Cooked Meals", SATURDAY);

        assertEquals("CATERER", sentRequest("/api/v1/predict/surplus").path("donorType").asText());
    }

    @Test
    void noPreviousDonationSendsZero() throws Exception {
        when(history.latestQuantities(eq(2L), eq(1), any())).thenReturn(List.of());
        when(client.post(anyString(), any())).thenReturn(call(SURPLUS_JSON, 5));

        service.surplus(2L, "Cooked Meals", SATURDAY);

        assertEquals(0.0, sentRequest("/api/v1/predict/surplus").path("previousQuantityKg").asDouble());
    }

    @Test
    void aiDownGivesAiAvailableFalseAndStillStoresARow() {
        when(client.post(anyString(), any())).thenThrow(new AiPredictionClient.AiUnavailableException("down"));

        AiPredictionResponse r = service.surplus(2L, "Cooked Meals", SATURDAY);

        assertFalse(r.aiAvailable());
        assertNull(r.predictedKg());
        assertEquals("down", r.message());
        Prediction p = savedPrediction();
        assertNull(p.getPredictedValue());
        assertEquals("ai-unavailable", p.getModelName());
        assertTrue(p.getOutputJson().contains("\"aiAvailable\":false"));
    }

    @Test
    void aiRejectionIsReportedAsNotAvailableWithTheReason() {
        when(client.post(anyString(), any())).thenThrow(new AiPredictionClient.AiRejectedException("unknown area"));

        AiPredictionResponse r = service.surplus(2L, "Cooked Meals", SATURDAY);

        assertFalse(r.aiAvailable());
        assertEquals("unknown area", r.message());
    }

    @Test
    void responseWithoutTheNumberIsTreatedAsUnavailable() throws Exception {
        when(client.post(anyString(), any())).thenReturn(call("{}", 5));

        AiPredictionResponse r = service.surplus(2L, "Cooked Meals", SATURDAY);

        assertFalse(r.aiAvailable());
        assertNotNull(r.message());
    }

    @Test
    void aFailureToStoreDoesNotBreakTheAnswer() throws Exception {
        when(client.post(anyString(), any())).thenReturn(call(SURPLUS_JSON, 5));
        when(predictions.save(any())).thenThrow(new RuntimeException("db down"));

        AiPredictionResponse r = service.surplus(2L, "Cooked Meals", SATURDAY);

        assertTrue(r.aiAvailable());
        assertEquals(23.8, r.predictedKg());
    }

    @Test
    void unknownCategoryIsABadRequestAndTheAiIsNotCalled() {
        when(categories.findByName("Pizza")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.surplus(2L, "Pizza", SATURDAY));

        verifyNoInteractions(client);
        verify(predictions, never()).save(any());
    }

    @Test
    void missingDonorProfileIsNotFound() {
        when(donors.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.surplus(99L, "Cooked Meals", SATURDAY));
    }

    // ---------------------------------------------------------------- demand

    @Test
    void demandBuildsTheRequestAndLinksTheNgo() throws Exception {
        NgoProfile ngo = new NgoProfile();
        ngo.setUserId(6L);
        when(ngos.findById(6L)).thenReturn(Optional.of(ngo));
        when(client.post(anyString(), any())).thenReturn(call(DEMAND_JSON, 30));

        AiPredictionResponse r = service.demand(6L, "NGO", "Cooked Meals", "Ukkadam", SATURDAY, null);

        assertTrue(r.aiAvailable());
        assertEquals(59.7, r.predictedKg());
        JsonNode req = sentRequest("/api/v1/predict/demand");
        assertEquals("Ukkadam", req.path("area").asText());
        assertEquals("SAT", req.path("dayOfWeek").asText());
        assertEquals(11, req.path("month").asInt());
        assertEquals(18, req.path("hourOfDay").asInt());
        Prediction p = savedPrediction();
        assertEquals(PredictionType.DEMAND, p.getPredictionType());
        assertSame(ngo, p.getNgo());
        assertNull(p.getDonor());
        assertEquals("RandomForestRegressor", p.getModelName());
    }

    @Test
    void demandForAnAdminHasNoNgoLink() throws Exception {
        when(client.post(anyString(), any())).thenReturn(call(DEMAND_JSON, 30));

        service.demand(1L, "ADMIN", "Cooked Meals", "Ukkadam", SATURDAY, 8);

        assertNull(savedPrediction().getNgo());
        assertEquals(8, sentRequest("/api/v1/predict/demand").path("hourOfDay").asInt());
        verify(ngos, never()).findById(any());
    }

    @Test
    void demandRejectsABadHourAndABlankArea() {
        assertThrows(BadRequestException.class,
                () -> service.demand(1L, "ADMIN", "Cooked Meals", "Ukkadam", SATURDAY, 24));
        assertThrows(BadRequestException.class,
                () -> service.demand(1L, "ADMIN", "Cooked Meals", "  ", SATURDAY, 10));
        verifyNoInteractions(client);
    }

    // ---------------------------------------------------------------- status

    @Test
    void statusWhenNoKeyIsConfigured() {
        when(client.isConfigured()).thenReturn(false);

        AiStatusResponse s = service.status();

        assertFalse(s.configured());
        assertFalse(s.reachable());
        assertTrue(s.message().contains("AI_SERVICE_API_KEY"));
        verify(client, never()).get(anyString());
    }

    @Test
    void statusWhenTheServiceIsUpAndAcceptsTheKey() throws Exception {
        when(client.isConfigured()).thenReturn(true);
        when(client.get("/health")).thenReturn(call(
                "{\"status\":\"UP\",\"modelsLoaded\":{\"surplus\":true,\"demand\":true},"
                        + "\"modelVersion\":\"1.0.0\",\"trainedOnSyntheticData\":true}", 7));
        when(client.get("/api/v1/models/info")).thenReturn(call(
                "{\"models\":[{\"name\":\"surplus\",\"algorithm\":\"GradientBoostingRegressor\","
                        + "\"metrics\":{\"mae\":1.9843,\"r2\":0.9605}}]}", 9));

        AiStatusResponse s = service.status();

        assertTrue(s.reachable());
        assertTrue(s.keyAccepted());
        assertEquals("1.0.0", s.modelVersion());
        assertEquals(Boolean.TRUE, s.modelsLoaded().get("surplus"));
        assertEquals(1, s.models().size());
        assertEquals(1.9843, s.models().get(0).get("mae"));
        assertEquals(7, s.latencyMs());
    }

    @Test
    void statusWhenTheKeyIsNotAccepted() throws Exception {
        when(client.isConfigured()).thenReturn(true);
        when(client.get("/health")).thenReturn(call("{\"status\":\"UP\"}", 7));
        when(client.get("/api/v1/models/info")).thenThrow(new AiPredictionClient.AiUnavailableException("HTTP 401"));

        AiStatusResponse s = service.status();

        assertTrue(s.reachable());
        assertFalse(s.keyAccepted());
        assertTrue(s.message().contains("API key"));
    }

    @Test
    void statusWhenTheServiceIsDown() {
        when(client.isConfigured()).thenReturn(true);
        when(client.get("/health")).thenThrow(new AiPredictionClient.AiUnavailableException("connection refused"));

        AiStatusResponse s = service.status();

        assertTrue(s.configured());
        assertFalse(s.reachable());
        assertEquals("connection refused", s.message());
    }
}