package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ParcelCreationServiceTest {
    @Test
    void createsParcelAndInitialHistoryUsingPricingQuote() {
        ParcelRepository parcels = mock(ParcelRepository.class);
        ParcelStatusHistoryRepository history = mock(ParcelStatusHistoryRepository.class);
        PricingClient pricing = mock(PricingClient.class);
        TrackingNumberGenerator tracking = mock(TrackingNumberGenerator.class);
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        when(tracking.generate()).thenReturn("SDTEST001");
        when(parcels.saveAndFlush(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID userId = UUID.randomUUID();
        CreateParcelRequest request = new CreateParcelRequest("Nimal", "0771234567",
            "Colombo", "Kandy", new BigDecimal("2.500"), "MEDIUM", "STANDARD");
        var response = new ParcelCreationService(parcels, history, pricing, tracking)
            .createCustomerParcel(request, userId);

        ArgumentCaptor<Parcel> parcelCapture = ArgumentCaptor.forClass(Parcel.class);
        verify(parcels).saveAndFlush(parcelCapture.capture());
        Parcel saved = parcelCapture.getValue();
        assertEquals(userId, saved.getSenderId());
        assertEquals(userId, saved.getCreatedBy());
        assertEquals(ParcelStatus.CREATED, saved.getStatus());
        assertEquals(new BigDecimal("450.00"), saved.getCharge());
        assertEquals("SDTEST001", response.trackingNumber());

        ArgumentCaptor<ParcelStatusHistory> historyCapture =
            ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(history).save(historyCapture.capture());
        assertNull(historyCapture.getValue().getPreviousStatus());
        assertEquals(ParcelStatus.CREATED, historyCapture.getValue().getNewStatus());
    }
}
