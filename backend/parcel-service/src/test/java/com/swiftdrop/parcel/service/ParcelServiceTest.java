package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParcelServiceTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final ParcelStatusHistoryRepository history = mock(ParcelStatusHistoryRepository.class);
    private final ParcelService service = new ParcelService(parcels, history,
            new ParcelStatusTransitionValidator());
    private final UUID actor = UUID.randomUUID();

    @Test
    void creationRecordsNullPreviousStatus() {
        Parcel parcel = new Parcel();
        parcel.setCreatedBy(actor);
        when(parcels.save(parcel)).thenReturn(parcel);

        assertThat(service.createParcel(parcel)).isSameAs(parcel);

        ParcelStatusHistory entry = recordedHistory();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(entry.getParcel()).isSameAs(parcel);
        assertThat(entry.getPreviousStatus()).isNull();
        assertThat(entry.getNewStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(entry.getChangedBy()).isEqualTo(actor);
        assertThat(entry.getNote()).isEqualTo("Parcel created");
        assertThat(entry.getChangedAt()).isNotNull();
    }

    @Test
    void statusChangeRecordsPreviousAndNewStatus() {
        UUID id = UUID.randomUUID();
        Parcel parcel = new Parcel();
        parcel.setStatus(ParcelStatus.CREATED);
        when(parcels.findForStatusUpdateById(id)).thenReturn(Optional.of(parcel));

        service.changeStatus(id, ParcelStatus.PICKED_UP, actor, "Courier collected parcel");

        ParcelStatusHistory entry = recordedHistory();
        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(entry.getParcel()).isSameAs(parcel);
        assertThat(entry.getPreviousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(entry.getNewStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(entry.getChangedBy()).isEqualTo(actor);
        assertThat(entry.getNote()).isEqualTo("Courier collected parcel");
    }

    @Test
    void invalidTransitionDoesNotChangeParcelOrWriteHistory() {
        UUID id = UUID.randomUUID();
        Parcel parcel = new Parcel();
        parcel.setStatus(ParcelStatus.CREATED);
        when(parcels.findForStatusUpdateById(id)).thenReturn(Optional.of(parcel));

        assertThatIllegalArgumentException().isThrownBy(() ->
                service.changeStatus(id, ParcelStatus.DELIVERED, actor, "Invalid"));

        assertThat(parcel.getStatus()).isEqualTo(ParcelStatus.CREATED);
        verifyNoInteractions(history);
    }

    @Test
    void existingParcelCannotBeCreatedAgain() {
        Parcel parcel = new Parcel();
        parcel.setId(UUID.randomUUID());
        assertThatIllegalArgumentException().isThrownBy(() -> service.createParcel(parcel));
        verifyNoInteractions(parcels, history);
    }

    private ParcelStatusHistory recordedHistory() {
        ArgumentCaptor<ParcelStatusHistory> entry = ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(history).save(entry.capture());
        return entry.getValue();
    }
}
