package com.swiftdrop.pricing.controller;

import com.swiftdrop.pricing.config.SecurityConfig;
import com.swiftdrop.pricing.exception.RateNotFoundException;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import com.swiftdrop.pricing.service.*;
import com.swiftdrop.pricing.strategy.*;
import java.util.Optional;
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
    ZoneBasedPricingStrategy.class, WeightBasedPricingStrategy.class,
    SizeSurchargePricingStrategy.class, ExpressPricingStrategy.class})
@WithMockUser(roles = "CUSTOMER")
class PricingQuoteControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ZoneService zones;
    @MockitoBean RateCardService rateCard;

    private String body(String city, String weight, String type, String size) {
        return "{\"pickupCity\":\"" + city + "\",\"deliveryCity\":\"Kandy\",\"weight\":" + weight
            + ",\"serviceType\":\"" + type + "\",\"size\":\"" + size + "\"}";
    }
    private org.springframework.test.web.servlet.ResultActions quote(String body) throws Exception {
        return mvc.perform(post("/api/pricing/quote").contentType("application/json").content(body));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "null"})
    void invalidWeight(String weight) throws Exception {
        quote(body("Colombo", weight, "STANDARD", "SMALL"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString())
            .andExpect(jsonPath("$.fields.weight").isString());
        verifyNoInteractions(zones, rateCard);
    }
    @Test void missingCityAndSize() throws Exception {
        quote(body("", "1", "STANDARD", ""))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.pickupCity").isString())
            .andExpect(jsonPath("$.fields.size").isString());
    }
    @Test void unknownTypeIsRejectedBeforeUnknownCity() throws Exception {
        quote(body("Unknown", "1", "OVERNIGHT", "SMALL"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Service type must be STANDARD or EXPRESS"));
        verifyNoInteractions(zones, rateCard);
    }
    @Test void unknownSizeIs400() throws Exception {
        quote(body("Colombo", "1", "STANDARD", "HUGE"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("Size must be SMALL, MEDIUM or LARGE"));
    }
    @Test void unknownLocalityIs404() throws Exception {
        when(zones.getZoneByLocality("Unknown")).thenThrow(new ZoneNotFoundException("Unknown"));
        quote(body("Unknown", "1", "STANDARD", "SMALL"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("No active zone covers: Unknown"));
    }
    @Test void routeWithoutRateIs404() throws Exception {
        var rates = rates();
        when(zones.getZoneByLocality(anyString())).thenReturn(western());
        when(rateCard.currentVersion()).thenReturn(Optional.of(rates));
        when(rateCard.getRoute(any(), any(), any())).thenThrow(new RateNotFoundException("No published rate within Western Metro"));
        quote(body("Colombo", "1", "STANDARD", "SMALL"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("No published rate within Western Metro"));
    }
    @Test void exampleUsesAmountAndBalancedBreakdown() throws Exception {
        var rates = rates();
        when(zones.getZoneByLocality("Colombo")).thenReturn(western());
        when(zones.getZoneByLocality("Kandy")).thenReturn(central());
        when(rateCard.currentVersion()).thenReturn(Optional.of(rates));
        when(rateCard.getRoute(any(), any(), any())).thenReturn(route());
        quote(body("Colombo", "2.3", "EXPRESS", "MEDIUM"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(1050.00))
            .andExpect(jsonPath("$.charge").doesNotExist()).andExpect(jsonPath("$.currency").value("LKR"))
            .andExpect(jsonPath("$.breakdown.baseFee").value(350.00))
            .andExpect(jsonPath("$.breakdown.weightFee").value(200.00))
            .andExpect(jsonPath("$.breakdown.sizeFee").value(150.00))
            .andExpect(jsonPath("$.breakdown.expressFee").value(350.00));
    }
}
