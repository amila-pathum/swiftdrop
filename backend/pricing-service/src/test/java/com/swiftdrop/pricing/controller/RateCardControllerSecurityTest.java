package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.config.SecurityConfig;
import com.swiftdrop.pricing.dto.response.RateCardResponse;
import com.swiftdrop.pricing.service.RateCardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.swiftdrop.pricing.PricingFixtures.*;

@WebMvcTest(controllers = RateCardController.class,
    properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Import(SecurityConfig.class)
class RateCardControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean RateCardService service;
    private static final String BODY = """
        {"basedOnVersion":3,"sizeSurcharges":{"small":0,"medium":150,"large":300},
         "routes":[{"originZoneId":"%s","destinationZoneId":"%s","baseWeightKg":1,
                    "baseFee":350,"additionalKgFee":100,"expressMultiplier":1.5}]}
        """.formatted(WESTERN_ID, CENTRAL_ID);
    private static final RateCardResponse CARD = new RateCardResponse(4, null, "admin",
        new RateCardResponse.SizeSurcharges(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN), List.of());

    @Test @WithMockUser(roles = "CUSTOMER")
    void onlyAdminsSeeOrPublishRates() throws Exception {
        mvc.perform(get("/api/pricing/rates")).andExpect(status().isForbidden());
        mvc.perform(get("/api/pricing/rates/versions")).andExpect(status().isForbidden());
        mvc.perform(post("/api/pricing/rates").contentType("application/json").content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(username = "admin-7", roles = "ADMIN")
    void adminPublishesAsThemselves() throws Exception {
        when(service.publish(any(), eq("admin-7"))).thenReturn(CARD);
        mvc.perform(post("/api/pricing/rates").contentType("application/json").content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(4));
    }
    @Test @WithMockUser(roles = "ADMIN")
    void rejectsInvalidRatesWithFieldErrors() throws Exception {
        mvc.perform(post("/api/pricing/rates").contentType("application/json").content(BODY
                .replace("\"expressMultiplier\":1.5", "\"expressMultiplier\":0.5")
                .replace("\"medium\":150", "\"medium\":-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields['routes[0].expressMultiplier']").value("Express multiplier must be at least 1.00"))
            .andExpect(jsonPath("$.fields['sizeSurcharges.medium']").value("Surcharge cannot be negative"));
        verifyNoInteractions(service);
    }
}
