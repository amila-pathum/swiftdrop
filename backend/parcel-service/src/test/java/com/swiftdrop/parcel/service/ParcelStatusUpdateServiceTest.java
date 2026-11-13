package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.request.UpdateParcelStatusRequest;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParcelStatusUpdateServiceTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final ParcelStatusHistoryRepository histories = mock(ParcelStatusHistoryRepository.class);
    private final CourierAssignmentVerifier assignments = mock(CourierAssignmentVerifier.class);
    private final ParcelStatusUpdateService service = new ParcelStatusUpdateService(
        parcels, histories, new ParcelStatusTransitionValidator(), assignments);
    private final UUID id = UUID.randomUUID();

    private Parcel existing(ParcelStatus status) {
        Parcel p = new Parcel();
        p.setId(id);
        p.setStatus(status);
        when(parcels.findForStatusUpdateById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void dispatcherUpdatesAndRecordsOneHistory() {
        Parcel p = existing(ParcelStatus.CREATED);
        var result = service.update(id, new UpdateParcelStatusRequest(ParcelStatus.PICKED_UP, "  collected  "), 42L, "DISPATCHER");
        assertThat(result.previousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(result.newStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(p.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        ArgumentCaptor<ParcelStatusHistory> captor = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(histories, times(1)).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getChangedBy()).isEqualTo(42L);
        assertThat(captor.getValue().getPreviousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(captor.getValue().getNote()).isEqualTo("collected");
    }

    @Test
    void unauthorizedCourierCannotMutate() {
        Parcel p = existing(ParcelStatus.CREATED);
        assertThatThrownBy(() -> service.update(id, new UpdateParcelStatusRequest(ParcelStatus.PICKED_UP, null), 88L, "COURIER"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(p.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verifyNoInteractions(histories);
    }

    @Test
    void assignedCourierCanUpdateAfterAssignmentVerification() {
        Parcel p = existing(ParcelStatus.CREATED);
        when(assignments.isAssigned(p, 88L)).thenReturn(true);
        service.update(id, new UpdateParcelStatusRequest(ParcelStatus.PICKED_UP, null), 88L, "COURIER");
        verify(histories).saveAndFlush(any(ParcelStatusHistory.class));
    }

    @Test
    void invalidMoveDoesNotWriteHistory() {
        Parcel p = existing(ParcelStatus.CREATED);
        assertThatThrownBy(() -> service.update(id, new UpdateParcelStatusRequest(ParcelStatus.DELIVERED, null), 42L, "ADMIN"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(p.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verifyNoInteractions(histories);
    }

    @Test
    void cancellationMustUseSeparateFlow() {
        existing(ParcelStatus.CREATED);
        assertThatThrownBy(() -> service.update(id, new UpdateParcelStatusRequest(ParcelStatus.CANCELLED, null), 42L, "ADMIN"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(histories);
    }

    @Test
    void unknownParcelReturnsNotFound() {
        when(parcels.findForStatusUpdateById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(id, new UpdateParcelStatusRequest(ParcelStatus.PICKED_UP, null), 42L, "ADMIN"))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
}
