package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.entity.*;
import com.swiftdrop.parcel.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.*;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParcelQueryServiceTest {
    private final ParcelRepository parcels = mock(ParcelRepository.class);
    private final ParcelStatusHistoryRepository history = mock(ParcelStatusHistoryRepository.class);
    private final ParcelQueryService service = new ParcelQueryService(parcels, history);
    private final UUID id = UUID.randomUUID();

    private Parcel existing() {
        Parcel p = new Parcel();
        p.setId(id); p.setSenderId(42L);
        when(parcels.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "ADMIN", "DISPATCHER"})
    void ownerAndStaffCanReadDetailsAndHistory(String role) {
        Parcel p = existing();
        Long actor = role.equals("CUSTOMER") ? 42L : 77L;
        when(history.findByParcelIdOrderByChangedAtAsc(id)).thenReturn(List.of(
            new ParcelStatusHistory(p, null, ParcelStatus.CREATED, 42L, "Created"),
            new ParcelStatusHistory(p, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, 77L, "Collected")));
        assertThat(service.getParcelById(id, actor, role).id()).isEqualTo(id);
        assertThat(service.getParcelHistory(id, actor, role))
            .extracting(entry -> entry.newStatus()).containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP);
    }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER", "MERCHANT", "COURIER"})
    void unauthorizedReadersCannotReadDetailsOrHistory(String role) {
        existing();
        assertThatThrownBy(() -> service.getParcelById(id, 77L, role))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.getParcelHistory(id, 77L, role))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(history);
    }

    @Test void missingParcelReturnsNotFound() {
        assertThatThrownBy(() -> service.getParcelById(id, 42L, "CUSTOMER"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test void invalidIdentityIsRejectedBeforeQuerying() {
        for (Long actor : Arrays.asList(null, 0L, -1L)) {
            assertThatThrownBy(() -> service.getParcelById(id, actor, "ADMIN"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
            assertThatThrownBy(() -> service.getMyParcels(actor, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        }
        assertThatThrownBy(() -> service.getParcelHistory(id, 42L, null))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(parcels, history);
    }

    @Test void customerPaginationPreservesFilterAndMetadata() {
        Parcel p = existing();
        Pageable page = PageRequest.of(1, 2, Sort.by("createdAt").descending());
        when(parcels.findBySenderId(42L, page)).thenReturn(new PageImpl<>(List.of(p), page, 3));
        var result = service.getMyParcels(42L, page);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getContent()).extracting(dto -> dto.id()).containsExactly(id);
        verify(parcels).findBySenderId(42L, page);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DISPATCHER"})
    void staffCanFilterByStatus(String role) {
        Pageable page = PageRequest.of(0, 10);
        when(parcels.findByStatus(ParcelStatus.CREATED, page)).thenReturn(Page.empty(page));
        assertThat(service.getParcelsByStatus(ParcelStatus.CREATED, role, page).isEmpty()).isTrue();
        verify(parcels).findByStatus(ParcelStatus.CREATED, page);
    }

    @Test void nonStaffCannotListByStatus() {
        for (String role : Arrays.asList(null, "CUSTOMER", "MERCHANT", "COURIER")) {
            assertThatThrownBy(() -> service.getParcelsByStatus(ParcelStatus.CREATED, role, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
        }
        verifyNoInteractions(parcels);
    }
}
