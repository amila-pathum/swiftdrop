package com.swiftdrop.pricing;

import com.swiftdrop.pricing.dto.request.QuoteRequest;
import com.swiftdrop.pricing.exception.*;
import com.swiftdrop.pricing.service.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.swiftdrop.pricing.PricingFixtures.*;

@ExtendWith(MockitoExtension.class)
class PricingQuoteServiceTest {
    @Mock ZoneService zones;
    @Mock PricingRuleService rules;
    PricingQuoteService service;
    @BeforeEach void setup() { service = new PricingQuoteService(zones, rules, calculator()); }
    @Test void exampleAndRoundedBreakdownAddUp() {
        when(zones.getZoneByCity("Colombo")).thenReturn(zone());
        when(zones.getZoneByCity("Kandy")).thenReturn(zone());
        var rule = rule();
        when(rules.getActivePricingRuleByZoneId(ZONE_ID)).thenReturn(rule);
        var quote = service.calculateQuote(new QuoteRequest("Colombo", "Kandy", new BigDecimal("2.5"), "EXPRESS"));
        assertThat(quote.amount()).isEqualTo(new BigDecimal("862.50"));
        assertThat(quote.breakdown()).containsEntry("baseFee", new BigDecimal("200.00"))
            .containsEntry("weightFee", new BigDecimal("375.00"))
            .containsEntry("expressFee", new BigDecimal("287.50"));
        rule.setBaseFee(new BigDecimal("10.05")); rule.setPricePerKg(new BigDecimal("7.77"));
        rule.setExpressMultiplier(new BigDecimal("1.15"));
        for (String type : new String[]{"STANDARD", "EXPRESS"}) {
            var rounded = service.calculateQuote(new QuoteRequest("Colombo", "Kandy", new BigDecimal("0.333"), type));
            assertThat(rounded.amount().scale()).isEqualTo(2);
            assertThat(rounded.breakdown().values()).allSatisfy(value -> assertThat(value.scale()).isEqualTo(2));
            assertThat(rounded.breakdown().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualTo(rounded.amount());
            assertThat(rounded.amount()).isEqualTo(new BigDecimal(type.equals("EXPRESS") ? "14.53" : "12.64"));
        }
    }
    @Test void rejectsTypeBeforeLookingUpZones() {
        assertThatThrownBy(() -> service.calculateQuote(new QuoteRequest("Unknown", "Unknown", BigDecimal.ONE, "OVERNIGHT")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(zones, rules);
    }
    @Test void reportsMissingZone() {
        when(zones.getZoneByCity("Unknown")).thenThrow(new ZoneNotFoundException("Unknown"));
        assertThatThrownBy(() -> service.calculateQuote(new QuoteRequest("Unknown", "Kandy", BigDecimal.ONE, "STANDARD")))
                .isInstanceOf(ZoneNotFoundException.class).hasMessage("No active zone found for city: Unknown");
        verifyNoInteractions(rules);
    }
    @Test void reportsMissingPricingRule() {
        when(zones.getZoneByCity(anyString())).thenReturn(zone());
        when(rules.getActivePricingRuleByZoneId(ZONE_ID)).thenThrow(new PricingRuleNotFoundException("No active pricing rule for zone " + ZONE_ID));
        assertThatThrownBy(() -> service.calculateQuote(new QuoteRequest("Colombo", "Kandy", BigDecimal.ONE, "STANDARD")))
                .isInstanceOf(PricingRuleNotFoundException.class);
    }
}
