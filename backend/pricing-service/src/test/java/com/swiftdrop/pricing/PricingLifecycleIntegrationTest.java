package com.swiftdrop.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@WithMockUser(roles = "ADMIN")
class PricingLifecycleIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;

    private String create(String path, String body) throws Exception {
        String json = mvc.perform(post(path).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json).results()
            .findFirst().orElseThrow().group(1);
    }

    @Test void persistsLifecycleAndRejectsConflictingReactivation() throws Exception {
        String zone = create("/api/zones", """
            {"name":"Kandy","city":" Kandy ","district":"Kandy"}
            """);
        mvc.perform(post("/api/zones").contentType("application/json").content("""
            {"name":"Duplicate","city":"kandy","district":"Kandy"}
            """)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").exists());
        String payload = "{\"zoneId\":\"" + zone + "\",\"baseFee\":200,\"pricePerKg\":150,\"expressMultiplier\":1.5}";
        String rule = create("/api/pricing/rules", payload);
        mvc.perform(patch("/api/pricing/rules/" + rule + "/disable")).andExpect(status().isOk());
        mvc.perform(get("/api/pricing/rules")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/pricing/rules?includeInactive=true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].zoneName").value("Kandy"));
        String replacement = create("/api/pricing/rules", payload);
        mvc.perform(patch("/api/pricing/rules/" + rule + "/enable"))
            .andExpect(status().isConflict());
        mvc.perform(patch("/api/pricing/rules/" + replacement + "/disable")).andExpect(status().isOk());
        mvc.perform(patch("/api/zones/" + zone + "/disable")).andExpect(status().isOk());
        mvc.perform(get("/api/zones")).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/zones?includeInactive=true")).andExpect(jsonPath("$[0].active").value(false));
        mvc.perform(patch("/api/pricing/rules/" + rule + "/enable")).andExpect(status().isConflict());
        mvc.perform(patch("/api/zones/" + zone + "/enable")).andExpect(status().isOk());
        mvc.perform(patch("/api/pricing/rules/" + rule + "/enable"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/pricing/quote").contentType("application/json").content("""
            {"pickupCity":"Kandy","deliveryCity":"kandy","weight":2.5,"serviceType":"EXPRESS"}
            """)).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(862.50));
    }
}
