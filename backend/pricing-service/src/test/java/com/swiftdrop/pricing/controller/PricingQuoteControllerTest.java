package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.config.SecurityConfig;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import com.swiftdrop.pricing.service.*;
import com.swiftdrop.pricing.strategy.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.swiftdrop.pricing.PricingFixtures.*;

@WebMvcTest(controllers = PricingQuoteController.class,
    properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
@Import({SecurityConfig.class, PricingQuoteService.class, PricingCalculator.class,
    ZoneBasedPricingStrategy.class, WeightBasedPricingStrategy.class, ExpressPricingStrategy.class})
@WithMockUser(roles = "CUSTOMER")
class PricingQuoteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ZoneService zones;
    @MockitoBean PricingRuleService rules;
    private String body(String city, String weight, String type) {
        return "{\"pickupCity\":\"" + city + "\",\"deliveryCity\":\"Kandy\",\"weight\":" + weight + ",\"serviceType\":\"" + type + "\"}";
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "null"})
    void invalidWeight(String weight) throws Exception {
        mvc.perform(post("/api/pricing/quote").contentType("application/json").content(body("Colombo", weight, "STANDARD")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString()).andExpect(jsonPath("$.fields.weight").isString());
        verifyNoInteractions(zones, rules);
    }
    @Test void missingCity() throws Exception {
        mvc.perform(post("/api/pricing/quote").contentType("application/json").content(body("", "1", "STANDARD")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.pickupCity").isString());
    }
    @Test void unknownTypeIsRejectedBeforeUnknownCity() throws Exception {
        mvc.perform(post("/api/pricing/quote").contentType("application/json").content(body("Unknown", "1", "OVERNIGHT")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Service type must be STANDARD or EXPRESS"));
        verifyNoInteractions(zones, rules);
    }
    @Test void unknownZoneIs404() throws Exception {
        when(zones.getZoneByCity("Unknown")).thenThrow(new ZoneNotFoundException("Unknown"));
        mvc.perform(post("/api/pricing/quote").contentType("application/json").content(body("Unknown", "1", "STANDARD")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("No active zone found for city: Unknown"));
    }
    @Test void exampleUsesAmountAndBalancedBreakdown() throws Exception {
        when(zones.getZoneByCity(anyString())).thenReturn(zone());
        when(rules.getActivePricingRuleByZoneId(ZONE_ID)).thenReturn(rule());
        mvc.perform(post("/api/pricing/quote").contentType("application/json").content(body("Colombo", "2.5", "EXPRESS")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(862.50))
            .andExpect(jsonPath("$.charge").doesNotExist()).andExpect(jsonPath("$.currency").value("LKR"))
            .andExpect(jsonPath("$.breakdown.baseFee").value(200.00))
            .andExpect(jsonPath("$.breakdown.weightFee").value(375.00))
            .andExpect(jsonPath("$.breakdown.expressFee").value(287.50));
    }
}
