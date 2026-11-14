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
        when(pricing.quote(any(), anyString())).thenReturn(new BigDecimal("450.00"));
        when(tracking.generate()).thenReturn("SDTEST001");
        when(parcels.saveAndFlush(any(Parcel.class))).thenAnswer(inv -> inv.getArgument(0));

        Long userId = 42L;
        CreateParcelRequest request = new CreateParcelRequest("Nimal", "0771234567",
            "10 Main Street", "Floor 2", "Colombo",
            "20 Lake Road", null, "Kandy",
            new BigDecimal("2.500"), "MEDIUM", "STANDARD");
        var response = new ParcelCreationService(parcels, history, pricing, tracking)
            .createCustomerParcel(request, userId, "customer-jwt");

        verify(pricing).quote(request, "customer-jwt");

        ArgumentCaptor<Parcel> parcelCapture = ArgumentCaptor.forClass(Parcel.class);
        verify(parcels).saveAndFlush(parcelCapture.capture());
        Parcel saved = parcelCapture.getValue();
        assertEquals(userId, saved.getSenderId());
        assertEquals(userId, saved.getCreatedBy());
        assertEquals(ParcelStatus.CREATED, saved.getStatus());
        assertEquals(new BigDecimal("450.00"), saved.getCharge());
        assertEquals("10 Main Street, Floor 2, Colombo", saved.getPickupAddress());
        assertEquals("20 Lake Road, Kandy", saved.getDeliveryAddress());
        assertEquals("SDTEST001", response.trackingNumber());

        ArgumentCaptor<ParcelStatusHistory> historyCapture =
            ArgumentCaptor.forClass(ParcelStatusHistory.class);
        verify(history).save(historyCapture.capture());
        assertNull(historyCapture.getValue().getPreviousStatus());
        assertEquals(ParcelStatus.CREATED, historyCapture.getValue().getNewStatus());
    }
}
