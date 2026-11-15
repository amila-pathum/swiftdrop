package com.swiftdrop.dispatch.service;

import com.swiftdrop.dispatch.client.DispatchDataClient;
import com.swiftdrop.dispatch.client.DispatchDataClient.CourierData;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.AssignmentResult;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.event.ParcelAssignedEvent;
import com.swiftdrop.dispatch.event.ParcelStatusChangedEvent;
import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.repository.DispatchRepository;
import com.swiftdrop.dispatch.repository.DispatchRepository.ActiveAssignment;
import com.swiftdrop.dispatch.repository.DispatchRepository.CourierAssignmentRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DispatchServiceTest {
    private static final long COURIER = 7L;
    private static final long COURIER_USER = 70L;
    private static final long OTHER_COURIER = 9L;

    private final DispatchRepository repository = mock(DispatchRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final DispatchDataClient dataClient = mock(DispatchDataClient.class);
    private final DispatchService service = new DispatchService(repository, events, dataClient);
    private final UUID parcelId = UUID.randomUUID();

    // ------------------------------------------------------------ assign

    @Test
    void assignFailsWhenParcelAlreadyHasActiveAssignment() {
        givenParcel("CREATED");
        when(repository.activeAssignment(parcelId)).thenReturn(Optional.of(active(OTHER_COURIER)));

        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);

        verifyNothingSaved();
    }

    @Test
    void assignFailsWhenParcelIsNotCreated() {
        givenParcel("PICKED_UP");

        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);

        verify(dataClient, never()).findCourier(anyLong());
        verifyNothingSaved();
    }

    @Test
    void assignFailsWhenParcelIsUnknown() {
        when(dataClient.findParcel(parcelId)).thenReturn(Optional.empty());

        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.NOT_FOUND);
    }

    @Test
    void assignFailsWhenCourierIsUnknown() {
        givenParcel("CREATED");
        when(dataClient.findCourier(COURIER)).thenReturn(Optional.empty());

        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.BAD_REQUEST);

        verifyNothingSaved();
    }

    @Test
    void assignFailsWhenCourierIsBusyOrOffline() {
        givenParcel("CREATED");
        for (String availability : List.of("BUSY", "OFFLINE")) {
            when(dataClient.findCourier(COURIER)).thenReturn(Optional.of(
                new CourierData(COURIER, COURIER_USER, "Nimal", availability, true)));

            assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);
        }
        verifyNothingSaved();
    }

    @Test
    void assignFailsWhenCourierIsInactive() {
        givenParcel("CREATED");
        when(dataClient.findCourier(COURIER)).thenReturn(Optional.of(
            new CourierData(COURIER, COURIER_USER, "Nimal", "AVAILABLE", false)));

        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);

        verifyNothingSaved();
    }

    @Test
    void assignRecordsTellsParcelServiceAndPublishes() {
        givenParcel("CREATED");
        givenAvailableCourier(COURIER, COURIER_USER);
        when(repository.insertAssignment(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(call -> result(call.getArgument(2), null));

        AssignmentResult result = service.assign(parcelId, COURIER, "42");

        assertThat(result.courierId()).isEqualTo(COURIER);
        verify(repository).insertAssignment(eq(parcelId), eq("SD-TEST"), eq(COURIER), eq(COURIER_USER),
            eq("Nimal"), isNull(), eq("42"), any(Instant.class));
        verify(dataClient).assignParcelCourier(parcelId, COURIER_USER);
        ArgumentCaptor<ParcelAssignedEvent> event = ArgumentCaptor.forClass(ParcelAssignedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().courierId()).isEqualTo("7");
        assertThat(event.getValue().courierUserId()).isEqualTo("70");
    }

    @Test
    void assignDoesNotPublishWhenParcelServiceRefuses() {
        givenParcel("CREATED");
        givenAvailableCourier(COURIER, COURIER_USER);
        when(repository.insertAssignment(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(call -> result(call.getArgument(2), null));
        doThrow(new DispatchException(HttpStatus.SERVICE_UNAVAILABLE, "Parcel service is unavailable"))
            .when(dataClient).assignParcelCourier(parcelId, COURIER_USER);

        // The exception leaves the @Transactional method, so the insert is rolled back
        // (covered end to end in DispatchServiceApplicationTests).
        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.SERVICE_UNAVAILABLE);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void concurrentSecondActiveAssignmentIsAConflict() {
        givenParcel("CREATED");
        givenAvailableCourier(COURIER, COURIER_USER);
        when(repository.insertAssignment(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new DataIntegrityViolationException("uq_dispatch_assignment_active_parcel"));

        assertStatus(() -> service.assign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);

        verify(dataClient, never()).assignParcelCourier(any(), any());
    }

    // ------------------------------------------------------------ reassign / cancel

    @Test
    void reassignRequiresAnActiveAssignment() {
        givenParcel("CREATED");
        when(repository.activeAssignment(parcelId)).thenReturn(Optional.empty());

        assertStatus(() -> service.reassign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);
    }

    @Test
    void reassignIsRejectedAfterPickup() {
        givenParcel("PICKED_UP");

        assertStatus(() -> service.reassign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);

        verify(repository, never()).cancelActiveAssignment(any(), any(), any());
    }

    @Test
    void reassignToTheSameCourierIsRejected() {
        givenParcel("CREATED");
        when(repository.activeAssignment(parcelId)).thenReturn(Optional.of(active(COURIER)));

        assertStatus(() -> service.reassign(parcelId, COURIER, "42"), HttpStatus.CONFLICT);
    }

    @Test
    void reassignCancelsTheOldRowAndAssignsTheNewCourier() {
        givenParcel("CREATED");
        when(repository.activeAssignment(parcelId)).thenReturn(Optional.of(active(OTHER_COURIER)));
        givenAvailableCourier(COURIER, COURIER_USER);
        when(repository.insertAssignment(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenAnswer(call -> result(call.getArgument(2), call.getArgument(5)));

        AssignmentResult result = service.reassign(parcelId, COURIER, "42");

        assertThat(result.previousCourierId()).isEqualTo(OTHER_COURIER);
        verify(repository).cancelActiveAssignment(eq(parcelId), eq("42"), any(Instant.class));
        verify(dataClient).assignParcelCourier(parcelId, COURIER_USER);
    }

    @Test
    void cancelIsRejectedAfterPickup() {
        givenParcel("IN_TRANSIT");

        assertStatus(() -> service.cancelAssignment(parcelId, "42"), HttpStatus.CONFLICT);

        verify(repository, never()).cancelActiveAssignment(any(), any(), any());
    }

    @Test
    void cancelWithoutActiveAssignmentIsAConflict() {
        givenParcel("CREATED");
        when(repository.cancelActiveAssignment(eq(parcelId), anyString(), any())).thenReturn(Optional.empty());

        assertStatus(() -> service.cancelAssignment(parcelId, "42"), HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------ reads

    @Test
    void unassignedSkipsParcelsWithAnActiveAssignment() {
        ParcelRow waiting = parcel(UUID.randomUUID(), "CREATED");
        ParcelRow assigned = parcel(UUID.randomUUID(), "CREATED");
        when(dataClient.createdParcels()).thenReturn(List.of(waiting, assigned));
        when(repository.activeAssignedParcelIds()).thenReturn(Set.of(assigned.parcelId()));

        var page = service.unassigned(null, null, null, null, null, 0, 20, "createdAt", "asc");

        assertThat(page.content()).extracting(ParcelRow::parcelId).containsExactly(waiting.parcelId());
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void courierWorkloadAddsActiveCountsAndAvailability() {
        when(dataClient.couriers()).thenReturn(List.of(
            new CourierData(COURIER, COURIER_USER, "Amal", "AVAILABLE", true),
            new CourierData(OTHER_COURIER, 90L, "Bimal", "BUSY", true)));
        when(repository.activeAssignmentCounts()).thenReturn(java.util.Map.of(COURIER, 2L));

        var couriers = service.couriers();

        assertThat(couriers).hasSize(2);
        assertThat(couriers.get(0).activeParcelCount()).isEqualTo(2);
        assertThat(couriers.get(0).available()).isTrue();
        assertThat(couriers.get(1).activeParcelCount()).isZero();
        assertThat(couriers.get(1).available()).isFalse();
        assertThat(couriers.get(1).availability()).isEqualTo("BUSY");
    }

    @Test
    void aCourierCanOnlyReadTheirOwnAssignments() {
        when(repository.courierAssignments(COURIER)).thenReturn(List.of(new CourierAssignmentRecord(
            UUID.randomUUID(), parcelId, COURIER, COURIER_USER, "ACTIVE", 0, Instant.now(), 1)));

        assertThat(service.courierAssignments(COURIER, COURIER_USER, true)).hasSize(1);
        assertThat(service.courierAssignments(COURIER, null, false)).hasSize(1);
        assertStatus(() -> service.courierAssignments(COURIER, 999L, true), HttpStatus.FORBIDDEN);
    }

    // ------------------------------------------------------------ events & reports

    @Test
    void acceptsCreationEventWithNullOldStatus() {
        var event = statusEvent(null, "CREATED");
        service.applyStatusEvent(event);
        verify(repository).applyStatusEvent(event);
    }

    @Test
    void rejectsNullOldStatusForNonCreationEvent() {
        var event = statusEvent(null, "PICKED_UP");
        assertStatus(() -> service.applyStatusEvent(event), HttpStatus.BAD_REQUEST);
        verify(repository, never()).applyStatusEvent(event);
    }

    @Test
    void rejectsUnknownStatusAndNonUuidParcelId() {
        assertStatus(() -> service.applyStatusEvent(statusEvent("CREATED", "LOST")), HttpStatus.BAD_REQUEST);
        var badParcel = new ParcelStatusChangedEvent(UUID.randomUUID(), "ParcelStatusChanged", Instant.now(),
            "SD-1", "not-a-uuid", null, null, "42", null, "CREATED", "PICKED_UP");
        assertStatus(() -> service.applyStatusEvent(badParcel), HttpStatus.BAD_REQUEST);
    }

    @Test
    void percentagesAreRoundedToTwoDecimals() {
        assertThat(DispatchService.percentage(2, 3)).isEqualByComparingTo("66.67");
        assertThat(DispatchService.percentage(0, 0)).isEqualByComparingTo("0.00");
    }

    // ------------------------------------------------------------ helpers

    private void givenParcel(String status) {
        when(dataClient.findParcel(parcelId)).thenReturn(Optional.of(parcel(parcelId, status)));
    }

    private void givenAvailableCourier(long id, long userId) {
        when(dataClient.findCourier(id)).thenReturn(Optional.of(
            new CourierData(id, userId, "Nimal", "AVAILABLE", true)));
    }

    private ActiveAssignment active(long courierId) {
        return new ActiveAssignment(UUID.randomUUID(), "SD-TEST", courierId, courierId * 10, "Old courier",
            "42", Instant.now());
    }

    private AssignmentResult result(Long courierId, Long previousCourierId) {
        return new AssignmentResult(UUID.randomUUID(), parcelId, "SD-TEST", courierId, "Nimal",
            previousCourierId, "42", Instant.now(), "ACTIVE");
    }

    private void verifyNothingSaved() {
        verify(repository, never()).insertAssignment(any(), any(), any(), any(), any(), any(), any(), any());
        verify(dataClient, never()).assignParcelCourier(any(), any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    private static ParcelRow parcel(UUID id, String status) {
        Instant now = Instant.now();
        return new ParcelRow(id, "SD-TEST", "Colombo", "Kandy", "Colombo 03", "Kandy Town", "SMALL",
            status, null, BigDecimal.TEN, now, now);
    }

    private ParcelStatusChangedEvent statusEvent(String oldStatus, String newStatus) {
        return new ParcelStatusChangedEvent(UUID.randomUUID(), "ParcelStatusChanged", Instant.now(),
            "SD-TEST", parcelId.toString(), null, null, "42", null, oldStatus, newStatus);
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            HttpStatus status) {
        assertThatThrownBy(action)
            .isInstanceOf(DispatchException.class)
            .extracting("status").isEqualTo(status);
    }
}
