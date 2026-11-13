package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.event.ParcelAssignedEvent;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.repository.DispatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchServiceTest {
    private final DispatchRepository repository = mock(DispatchRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DispatchService service = new DispatchService(repository, events);
    private final UUID parcelId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();

    @Test
    void rejectsDoubleAssignmentToSameCourier() {
        when(repository.lockParcel(parcelId)).thenReturn(Optional.of(parcel("CREATED", courierId)));
        when(repository.isCourier(courierId)).thenReturn(true);

        assertConflict(() -> service.assign(parcelId, courierId, "42"));

        verify(repository, never()).saveAssignment(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
        verify(events, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void rejectsInvalidCourier() {
        when(repository.lockParcel(parcelId)).thenReturn(Optional.of(parcel("CREATED", null)));
        when(repository.isCourier(courierId)).thenReturn(false);

        assertThatThrownBy(() -> service.assign(parcelId, courierId, "42"))
            .isInstanceOf(DispatchException.class)
            .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).saveAssignment(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReassignmentAfterPickup() {
        when(repository.lockParcel(parcelId)).thenReturn(Optional.of(parcel("PICKED_UP", courierId)));
        UUID replacementCourierId = UUID.randomUUID();
        when(repository.isCourier(replacementCourierId)).thenReturn(true);

        assertConflict(() -> service.assign(parcelId, replacementCourierId, "42"));
    }

    @Test
    void recordsAndPublishesAValidAssignment() {
        when(repository.lockParcel(parcelId)).thenReturn(Optional.of(parcel("CREATED", null)));
        when(repository.isCourier(courierId)).thenReturn(true);

        service.assign(parcelId, courierId, "42");

        verify(repository).saveAssignment(
            org.mockito.ArgumentMatchers.eq(parcelId),
            org.mockito.ArgumentMatchers.eq(courierId),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.eq("42"),
            org.mockito.ArgumentMatchers.any(Instant.class));
        verify(events).publishEvent(any(ParcelAssignedEvent.class));
    }

    private ParcelRow parcel(String status, UUID courierId) {
        Instant now = Instant.now();
        return new ParcelRow(parcelId, "SD-TEST", "Colombo", "Kandy", "Colombo",
            "Kandy", "SMALL", status, courierId, "Courier", BigDecimal.TEN, now, now);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOf(DispatchException.class)
            .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }
}
