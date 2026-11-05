package com.foodloop.integration;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Needs the real MySQL database (schema.sql + sample_data.sql loaded) and application-local.yml.
 * Run with:  mvn test -Pintegration
 * Everything it writes is rolled back (class-level @Transactional).
 */
@Tag("integration")
@SpringBootTest(properties = {
        "app.jwt.secret=IntegrationTestSecretKeyAtLeast32Characters!",
        "app.rate-limit.per-minute=100"})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private String tokenFor(String email) throws Exception {
        MvcResult r = login(email, "Password@123");
        assertEquals(200, r.getResponse().getStatus());
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void protectedEndpointWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void sampleAdminCanLogin() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@foodloop.test\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        assertEquals(401, login("admin@foodloop.test", "WrongPass1!").getResponse().getStatus());
    }

    @Test
    void unknownEmailGivesSameMessageAsWrongPassword() throws Exception {
        String wrong = om.readTree(login("admin@foodloop.test", "WrongPass1!").getResponse().getContentAsString()).get("message").asText();
        MvcResult unknown = login("nobody@foodloop.test", "WrongPass1!");
        assertEquals(401, unknown.getResponse().getStatus());
        assertEquals(wrong, om.readTree(unknown.getResponse().getContentAsString()).get("message").asText());
    }

    @Test
    void tokenGivesAccessToOwnProfile() throws Exception {
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + tokenFor("hopemeals@foodloop.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("hopemeals@foodloop.test"))
                .andExpect(jsonPath("$.role").value("NGO"));
    }

    @Test
    void forgedTokenIs401() throws Exception {
        mvc.perform(get("/api/profile").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.e30.invalidsignature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void donorCannotReachAdminArea() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + tokenFor("annapoorna@foodloop.test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidatesTheToken() throws Exception {
        String token = tokenFor("vol.karthik@foodloop.test");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void weakPasswordRegistrationIs400WithFieldError() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson("weak.it@foodloop.test", "weak")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    void duplicateEmailRegistrationIs409() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson("admin@foodloop.test", "Passw0rd!x")))
                .andExpect(status().isConflict());
    }

    @Test
    void newVolunteerCanRegisterAndLogIn() throws Exception {
        String email = "vol.it." + System.currentTimeMillis() + "@foodloop.test";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerJson(email, "Passw0rd!x")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertEquals(200, login(email, "Passw0rd!x").getResponse().getStatus());
    }

    private String registerJson(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"IT Volunteer\","
                + "\"phone\":\"9876543210\",\"role\":\"VOLUNTEER\",\"addressLine\":\"1 Test Street\",\"city\":\"Coimbatore\","
                + "\"state\":\"Tamil Nadu\",\"pincode\":\"641001\",\"latitude\":11.0,\"longitude\":76.9,\"vehicleType\":\"BIKE\"}";
    }
}
