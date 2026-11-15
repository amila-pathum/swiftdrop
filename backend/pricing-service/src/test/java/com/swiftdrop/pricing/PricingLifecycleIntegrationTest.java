package com.swiftdrop.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Zones, publishing rate versions and quoting, end to end against PostgreSQL. */
@SpringBootTest(properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@WithMockUser(username = "admin-1", roles = "ADMIN")
// Start from no zones instead of the seeded provinces, so the test controls every zone.
@Sql(statements = {"DELETE FROM route_rates", "DELETE FROM rate_versions",
    "DELETE FROM zone_localities", "DELETE FROM zones"})
class PricingLifecycleIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String body) throws Exception {
        return mvc.perform(request.contentType("application/json").content(body));
    }
    private String idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json).results()
            .findFirst().orElseThrow().group(1);
    }
    private String publish(Integer basedOn, String routes) {
        return """
            {"basedOnVersion":%s,"sizeSurcharges":{"small":0,"medium":150,"large":300},"routes":[%s]}
            """.formatted(basedOn, routes);
    }
    private String route(String a, String b, int baseFee) {
        return """
            {"originZoneId":"%s","destinationZoneId":"%s","baseWeightKg":1,"baseFee":%d,
             "additionalKgFee":100,"expressMultiplier":1.5}
            """.formatted(a, b, baseFee);
    }
    private ResultActions quote(String from, String to, String weight, String type, String size) throws Exception {
        return send(post("/api/pricing/quote"), """
            {"pickupCity":"%s","deliveryCity":"%s","weight":%s,"serviceType":"%s","size":"%s"}
            """.formatted(from, to, weight, type, size));
    }

    @Test void zonesRatesAndQuotes() throws Exception {
        String western = idOf(send(post("/api/zones"), """
            {"name":"Western Metro","code":"wp","localities":[" Colombo ","Dehiwala","colombo",""]}
            """).andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("WP"))
            .andExpect(jsonPath("$.localities.length()").value(2)));
        String central = idOf(send(post("/api/zones"), """
            {"name":"Central Hills","code":"CP","localities":["Kandy","Peradeniya"]}
            """).andExpect(status().isCreated()));

        send(post("/api/zones"), """
            {"name":"Other","code":"WP","localities":["Galle"]}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Zone code WP is already used by Western Metro"));
        send(post("/api/zones"), """
            {"name":"Other","code":"SP","localities":["Galle","KANDY"]}
            """).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("KANDY is already covered by Central Hills"));

        // Reordering a zone's localities rewrites them without tripping the unique index.
        send(put("/api/zones/" + central), """
            {"name":"Central Hills","code":"CP","localities":["Peradeniya","Kandy","Matale"],"active":true}
            """).andExpect(status().isOk()).andExpect(jsonPath("$.localities[0]").value("Peradeniya"));

        // Before anything is published: every pair of active zones, with no rates.
        mvc.perform(get("/api/pricing/rates")).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(nullValue()))
            .andExpect(jsonPath("$.routes.length()").value(3))
            .andExpect(jsonPath("$.routes[1].origin.code").value("CP"))
            .andExpect(jsonPath("$.routes[1].destination.code").value("WP"))
            .andExpect(jsonPath("$.routes[1].baseFee").value(nullValue()));
        quote("Colombo", "Kandy", "2", "STANDARD", "SMALL").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("No delivery rates have been published yet"));

        send(post("/api/pricing/rates"), publish(null, route(western, central, 350)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.publishedBy").value("admin-1"))
            .andExpect(jsonPath("$.routes[1].baseFee").value(350.00));

        // Same price in both directions; locality matching ignores case.
        quote("Colombo", "Kandy", "2", "STANDARD", "SMALL").andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(450.00));
        quote("kandy", "dehiwala", "2.3", "EXPRESS", "MEDIUM").andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(1050.00));
        quote("Colombo", "Dehiwala", "1", "STANDARD", "SMALL").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("No published rate within Western Metro"));
        quote("Galle", "Kandy", "1", "STANDARD", "SMALL").andExpect(status().isNotFound());

        // Publishing from a stale version is refused.
        send(post("/api/pricing/rates"), publish(null, route(western, western, 300)))
            .andExpect(status().isConflict());

        // A disabled zone's rates are kept when publishing without it, and return with it.
        mvc.perform(patch("/api/zones/" + central + "/disable")).andExpect(status().isOk());
        quote("Colombo", "Kandy", "2", "STANDARD", "SMALL").andExpect(status().isNotFound());
        send(post("/api/pricing/rates"), publish(1, route(western, central, 999)))
            .andExpect(status().isBadRequest());
        send(post("/api/pricing/rates"), publish(1, route(western, western, 300)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.routes.length()").value(1));
        mvc.perform(patch("/api/zones/" + central + "/enable")).andExpect(status().isOk());
        mvc.perform(get("/api/pricing/rates"))
            .andExpect(jsonPath("$.routes[1].baseFee").value(350.00))
            .andExpect(jsonPath("$.routes[2].baseFee").value(300.00));
        quote("Colombo", "Kandy", "2", "STANDARD", "SMALL").andExpect(jsonPath("$.amount").value(450.00));

        mvc.perform(get("/api/pricing/rates/versions")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].version").value(2)).andExpect(jsonPath("$[0].routeCount").value(2))
            .andExpect(jsonPath("$[1].version").value(1)).andExpect(jsonPath("$[1].routeCount").value(1));
    }
}
