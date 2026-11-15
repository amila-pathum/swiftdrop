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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the whole service against a throwaway PostgreSQL (needs Docker). */
@SpringBootTest(properties = {
    "PRICING_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PricingServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mockMvc;

    @Test
    void contextLoadsAndReportsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    // The seeded provinces and v1 rates quote straight away.
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void seededRatesQuoteBetweenDistrictCapitals() throws Exception {
        String quote = """
            {"pickupCity":"%s","deliveryCity":"%s","weight":%s,"serviceType":"%s","size":"%s"}
            """;
        // Within Western: the minimum, LKR 350.
        mockMvc.perform(post("/api/pricing/quote").contentType("application/json")
                .content(quote.formatted("Colombo", "Gampaha", "1", "STANDARD", "SMALL")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(350.00));
        // Galle -> Jaffna: the longest route, LKR 2,000 for 1 kg.
        mockMvc.perform(post("/api/pricing/quote").contentType("application/json")
                .content(quote.formatted("Galle", "Jaffna", "1", "STANDARD", "SMALL")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(2000.00));
        // Colombo -> Jaffna, 2.3 kg, Medium, express: (1600 + 2 x 140 + 150) x 1.5 = 3045.
        mockMvc.perform(post("/api/pricing/quote").contentType("application/json")
                .content(quote.formatted("Colombo", "Jaffna", "2.3", "EXPRESS", "MEDIUM")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(3045.00));
    }
}
