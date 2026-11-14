package com.swiftdrop.pricing;

import com.swiftdrop.pricing.dto.request.QuoteRequest;
import com.swiftdrop.pricing.exception.*;
import com.swiftdrop.pricing.service.*;
import java.math.BigDecimal;
import java.util.Optional;
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
    @Mock RateCardService rateCard;
    PricingQuoteService service;

    @BeforeEach void setup() { service = new PricingQuoteService(zones, rateCard, calculator()); }

    private QuoteRequest request(String from, String to, String weight, String type, String size) {
        return new QuoteRequest(from, to, new BigDecimal(weight), type, size);
    }

    @Test void pricesTheRouteBetweenTheTwoZones() {
        var western = western(); var central = central(); var rates = rates();
        when(zones.getZoneByLocality("Colombo")).thenReturn(western);
        when(zones.getZoneByLocality("Kandy")).thenReturn(central);
        when(rateCard.currentVersion()).thenReturn(Optional.of(rates));
        when(rateCard.getRoute(rates, western, central)).thenReturn(route());

        var quote = service.calculateQuote(request("Colombo", "Kandy", "2.3", "express", "medium"));
        assertThat(quote.amount()).isEqualTo(new BigDecimal("1050.00"));
        assertThat(quote.currency()).isEqualTo("LKR");
        assertThat(quote.breakdown()).containsEntry("sizeFee", new BigDecimal("150.00"))
                .containsEntry("expressFee", new BigDecimal("350.00"));
    }

    @Test void rejectsServiceTypeAndSizeBeforeAnyLookup() {
        assertThatThrownBy(() -> service.calculateQuote(request("Nowhere", "Nowhere", "1", "OVERNIGHT", "SMALL")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Service type must be STANDARD or EXPRESS");
        assertThatThrownBy(() -> service.calculateQuote(request("Nowhere", "Nowhere", "1", "STANDARD", "HUGE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Size must be SMALL, MEDIUM or LARGE");
        verifyNoInteractions(zones, rateCard);
    }

    @Test void reportsALocalityNoZoneCovers() {
        when(zones.getZoneByLocality("Nowhere")).thenThrow(new ZoneNotFoundException("Nowhere"));
        assertThatThrownBy(() -> service.calculateQuote(request("Nowhere", "Kandy", "1", "STANDARD", "SMALL")))
                .isInstanceOf(ZoneNotFoundException.class).hasMessage("No active zone covers: Nowhere");
        verifyNoInteractions(rateCard);
    }

    @Test void reportsWhenNothingIsPublished() {
        when(zones.getZoneByLocality(anyString())).thenReturn(western());
        when(rateCard.currentVersion()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.calculateQuote(request("Colombo", "Dehiwala", "1", "STANDARD", "SMALL")))
                .isInstanceOf(RateNotFoundException.class).hasMessage("No delivery rates have been published yet");
    }
}
