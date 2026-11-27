package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.exception.PricingConflictException;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import com.swiftdrop.pricing.repository.RouteRateRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static com.swiftdrop.pricing.PricingFixtures.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ZoneServiceDeleteTest {
    ZoneRepository zones = mock(ZoneRepository.class);
    RouteRateRepository routes = mock(RouteRateRepository.class);
    ZoneService service = new ZoneService(zones, routes);

    @Test void deletesAZoneNoRateCardUses() {
        var zone = western();
        when(zones.findById(WESTERN_ID)).thenReturn(Optional.of(zone));
        service.deleteZone(WESTERN_ID);
        verify(zones).delete(zone);
    }
    @Test void refusesAZoneUsedByAnyRateCardVersion() {
        when(zones.findById(WESTERN_ID)).thenReturn(Optional.of(western()));
        when(routes.existsForZone(WESTERN_ID)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteZone(WESTERN_ID)).isInstanceOf(PricingConflictException.class)
            .hasMessageContaining("Disable it instead");
        verify(zones, never()).delete(any());
    }
    @Test void reportsAMissingZone() {
        UUID missing = UUID.randomUUID();
        when(zones.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteZone(missing)).isInstanceOf(ZoneNotFoundException.class);
    }
}
