package com.foodloop.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Full redistribution flow against the REAL MySQL (sample data loaded):
 * donor creates donation -> matching offers -> NGO accepts -> NGO creates pickup -> volunteer accepts ->
 * PICKED_UP -> IN_TRANSIT -> DELIVERED -> NGO confirms receipt -> donation COMPLETED.
 * Also: an expired donation cannot be accepted (409), and role/ownership rules around pickups.
 *
 * NOT transactional (several requests commit separately). It deletes the rows it creates and restores the
 * volunteer / NGO counters it touches. Run with:  mvn test -Pintegration
 * Every step reports its own name and the server's answer when it fails.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "app.jwt.secret=IntegrationTestSecretKeyAtLeast32Characters!",
        "app.rate-limit.per-minute=200"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FullFlowIntegrationTest {
    private static final String DONOR = "annapoorna@foodloop.test";
    private static final String VOLUNTEER = "vol.karthik@foodloop.test";
    private static final long VOLUNTEER_ID = 9L;
    private static final Map<Long, String> NGO_EMAIL = Map.of(
            6L, "hopemeals@foodloop.test", 7L, "annadhanam@foodloop.test", 8L, "littlehearts@foodloop.test");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> createdDonations = new ArrayList<>();
    private Map<String, Object> volunteerBefore;
    private final Map<Long, Object> ngoServedBefore = new HashMap<>();

    @BeforeEach
    void snapshotAndPrepare() {
        volunteerBefore = jdbc.queryForMap(
                "SELECT availability_status, total_deliveries FROM volunteer_profiles WHERE user_id = ?", VOLUNTEER_ID);
        for (Long ngoId : NGO_EMAIL.keySet()) {
            ngoServedBefore.put(ngoId, jdbc.queryForObject(
                    "SELECT beneficiaries_served FROM ngo_profiles WHERE user_id = ?", Object.class, ngoId));
        }
        jdbc.update("UPDATE volunteer_profiles SET availability_status = 'AVAILABLE' WHERE user_id = ?", VOLUNTEER_ID);
    }

    @AfterEach
    void cleanUp() {
        for (Long id : createdDonations) {
            jdbc.update("DELETE FROM deliveries WHERE pickup_request_id IN (SELECT id FROM pickup_requests WHERE donation_id = ?)", id);
            jdbc.update("DELETE FROM pickup_requests WHERE donation_id = ?", id);
            jdbc.update("DELETE FROM notifications WHERE related_entity_type = 'DONATION' AND related_entity_id = ?", id);
            jdbc.update("DELETE FROM audit_logs WHERE entity_type = 'DONATION' AND entity_id = ?", id);
            jdbc.update("DELETE FROM donations WHERE id = ?", id);        // matches cascade
        }
        createdDonations.clear();
        jdbc.update("UPDATE volunteer_profiles SET availability_status = ?, total_deliveries = ? WHERE user_id = ?",
                volunteerBefore.get("availability_status").toString(), volunteerBefore.get("total_deliveries"), VOLUNTEER_ID);
        ngoServedBefore.forEach((ngoId, served) ->
                jdbc.update("UPDATE ngo_profiles SET beneficiaries_served = ? WHERE user_id = ?", served, ngoId));
    }

    // ------------------------------------------------------------------ helpers
    private JsonNode ok(MvcResult r, String step) throws Exception {
        String body = r.getResponse().getContentAsString();
        int s = r.getResponse().getStatus();
        assertTrue(s >= 200 && s < 300, "STEP '" + step + "' failed: HTTP " + s + " " + body);
        return body.isBlank() ? om.nullNode() : om.readTree(body);
    }

    private String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Password@123\"}")).andReturn();
        return "Bearer " + ok(r, "login " + email).get("accessToken").asText();
    }

    private long createDonation(String donorToken) throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String json = "{\"categoryId\":1,\"foodName\":\"Full flow test meals\",\"quantity\":5,\"unit\":\"KG\","
                + "\"preparedAt\":\"" + now.minusHours(1) + "\",\"expiresAt\":\"" + now.plusHours(3) + "\","
                + "\"storageCondition\":\"HOT_HELD\",\"vegetarian\":true,"
                + "\"pickupWindowStart\":\"" + now.plusMinutes(10) + "\",\"pickupWindowEnd\":\"" + now.plusHours(2) + "\","
                + "\"safetyDeclared\":true}";
        MvcResult r = mvc.perform(post("/api/donations").header("Authorization", donorToken)
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
        assertEquals(201, r.getResponse().getStatus(), "STEP 'create donation' failed: " + r.getResponse().getContentAsString());
        long id = om.readTree(r.getResponse().getContentAsString()).get("id").asLong();
        createdDonations.add(id);
        return id;
    }

    private JsonNode firstOffer(String donorToken, long donationId) throws Exception {
        JsonNode offers = ok(mvc.perform(get("/api/donations/" + donationId + "/matches").header("Authorization", donorToken)).andReturn(), "list offers");
        assertTrue(offers.size() > 0, "STEP 'matching' produced no offers for donation " + donationId);
        return offers.get(0);
    }

    private String donationStatus(String donorToken, long donationId) throws Exception {
        return ok(mvc.perform(get("/api/donations/" + donationId).header("Authorization", donorToken)).andReturn(), "read donation").get("status").asText();
    }

    private MvcResult json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token, String body) throws Exception {
        return mvc.perform(b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    // ------------------------------------------------------------------ the full flow
    @Test
    void donationTravelsFromDonorToNgoThroughVolunteerAndEndsCompleted() throws Exception {
        String donor = login(DONOR);
        long donationId = createDonation(donor);
        assertEquals("MATCHED", donationStatus(donor, donationId), "after creation the donation should be MATCHED");

        JsonNode offer = firstOffer(donor, donationId);
        long ngoId = offer.get("ngoId").asLong();
        long matchId = offer.get("matchId").asLong();
        String ngo = login(NGO_EMAIL.get(ngoId));

        ok(mvc.perform(post("/api/ngo/matches/" + matchId + "/accept").header("Authorization", ngo)).andReturn(), "NGO accepts offer");
        assertEquals("ACCEPTED", donationStatus(donor, donationId));

        String scheduled = LocalDateTime.now().plusMinutes(30).withNano(0).toString();
        JsonNode pickup = ok(json(post("/api/ngo/pickups"), ngo,
                "{\"donationId\":" + donationId + ",\"scheduledPickupAt\":\"" + scheduled + "\"}"), "NGO creates pickup");
        long pickupId = pickup.get("id").asLong();

        String volunteer = login(VOLUNTEER);
        JsonNode available = ok(mvc.perform(get("/api/volunteer/tasks/available").header("Authorization", volunteer)).andReturn(), "volunteer lists available tasks");
        boolean listed = false;
        for (JsonNode t : available) if (t.get("id").asLong() == pickupId) listed = true;
        assertTrue(listed, "STEP 'available tasks': pickup " + pickupId + " is not offered to " + VOLUNTEER + " (distance / radius / availability rule?)");

        JsonNode accepted = ok(mvc.perform(post("/api/volunteer/pickups/" + pickupId + "/accept").header("Authorization", volunteer)).andReturn(), "volunteer accepts pickup");
        JsonNode deliveryIdNode = accepted.get("deliveryId");
        if (deliveryIdNode == null || deliveryIdNode.isNull()) {
            for (JsonNode t : ok(mvc.perform(get("/api/volunteer/tasks/assigned").header("Authorization", volunteer)).andReturn(), "volunteer lists assigned tasks")) {
                if (t.get("id").asLong() == pickupId) deliveryIdNode = t.get("deliveryId");
            }
        }
        assertNotNull(deliveryIdNode, "no deliveryId on the accepted pickup");
        assertTrue(!deliveryIdNode.isNull(), "deliveryId is null after the volunteer accepted pickup " + pickupId);
        long deliveryId = deliveryIdNode.asLong();
        assertEquals("VOLUNTEER_ASSIGNED", donationStatus(donor, donationId));

        String where = ",\"lat\":11.0074,\"lng\":76.95}";
        ok(json(patch("/api/volunteer/deliveries/" + deliveryId + "/status"), volunteer, "{\"status\":\"PICKED_UP\"" + where), "status PICKED_UP");
        assertEquals("PICKED_UP", donationStatus(donor, donationId));
        ok(json(patch("/api/volunteer/deliveries/" + deliveryId + "/status"), volunteer, "{\"status\":\"IN_TRANSIT\"" + where), "status IN_TRANSIT");
        assertEquals("IN_TRANSIT", donationStatus(donor, donationId));
        ok(json(patch("/api/volunteer/deliveries/" + deliveryId + "/status"), volunteer, "{\"status\":\"DELIVERED\"" + where), "status DELIVERED");
        assertEquals("DELIVERED", donationStatus(donor, donationId));

        ok(json(post("/api/ngo/deliveries/" + deliveryId + "/confirm"), ngo, "{\"quantityReceivedKg\":5,\"note\":\"integration test\"}"), "NGO confirms receipt");
        assertEquals("COMPLETED", donationStatus(donor, donationId));

        // database really agrees
        assertEquals("RECEIPT_CONFIRMED", jdbc.queryForObject("SELECT status FROM deliveries WHERE id = ?", String.class, deliveryId));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM donations WHERE id = ?", String.class, donationId));
    }

    @Test
    void expiredDonationCannotBeAccepted() throws Exception {
        String donor = login(DONOR);
        long donationId = createDonation(donor);
        JsonNode offer = firstOffer(donor, donationId);
        String ngo = login(NGO_EMAIL.get(offer.get("ngoId").asLong()));

        LocalDateTime now = LocalDateTime.now();      // keeps chk_don_times valid: prepared < expires, window end > start
        jdbc.update("UPDATE donations SET prepared_at = ?, expires_at = ?, pickup_window_start = ?, pickup_window_end = ? WHERE id = ?",
                Timestamp.valueOf(now.minusHours(3)), Timestamp.valueOf(now.minusHours(1)),
                Timestamp.valueOf(now.minusHours(2)), Timestamp.valueOf(now.minusMinutes(90)), donationId);

        MvcResult r = mvc.perform(post("/api/ngo/matches/" + offer.get("matchId").asLong() + "/accept").header("Authorization", ngo)).andReturn();
        assertEquals(409, r.getResponse().getStatus(), "accepting an expired donation must be 409, got: " + r.getResponse().getContentAsString());
    }

    @Test
    void pickupRulesAreEnforced() throws Exception {
        String donor = login(DONOR);
        long donationId = createDonation(donor);
        JsonNode offer = firstOffer(donor, donationId);
        long ngoId = offer.get("ngoId").asLong();
        String ngo = login(NGO_EMAIL.get(ngoId));
        String scheduled = LocalDateTime.now().plusMinutes(30).withNano(0).toString();
        String body = "{\"donationId\":" + donationId + ",\"scheduledPickupAt\":\"" + scheduled + "\"}";

        // not accepted yet -> no pickup can be created
        MvcResult early = json(post("/api/ngo/pickups"), ngo, body);
        assertTrue(early.getResponse().getStatus() >= 400 && early.getResponse().getStatus() < 500,
                "pickup before acceptance must be rejected, got HTTP " + early.getResponse().getStatus());

        ok(mvc.perform(post("/api/ngo/matches/" + offer.get("matchId").asLong() + "/accept").header("Authorization", ngo)).andReturn(), "NGO accepts offer");

        // a different NGO may not create the pickup
        for (Map.Entry<Long, String> other : NGO_EMAIL.entrySet()) {
            if (other.getKey() != ngoId) {
                MvcResult r = json(post("/api/ngo/pickups"), login(other.getValue()), body);
                assertTrue(r.getResponse().getStatus() >= 400 && r.getResponse().getStatus() < 500,
                        "another NGO must not create the pickup, got HTTP " + r.getResponse().getStatus());
                break;
            }
        }
        // role guards
        assertEquals(403, json(post("/api/ngo/pickups"), login(VOLUNTEER), body).getResponse().getStatus());
        assertEquals(403, json(post("/api/ngo/pickups"), donor, body).getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/volunteer/tasks/available").header("Authorization", ngo)).andReturn().getResponse().getStatus());
    }
}