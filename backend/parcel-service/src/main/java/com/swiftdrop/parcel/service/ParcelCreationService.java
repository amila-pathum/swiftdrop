package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

@Service
public class ParcelCreationService {
    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;
    private final PricingClient pricing;
    private final TrackingNumberGenerator tracking;

    public ParcelCreationService(ParcelRepository parcels,
                                 ParcelStatusHistoryRepository history,
                                 PricingClient pricing,
                                 TrackingNumberGenerator tracking) {
        this.parcels = parcels;
        this.history = history;
        this.pricing = pricing;
        this.tracking = tracking;
    }

    @Transactional
    public ParcelResponse createCustomerParcel(CreateParcelRequest request, Long userId) {
        // Quote before writing to the database; never trust a client-supplied charge.
        BigDecimal charge = pricing.quote(request);
        if (charge == null || charge.signum() < 0 || charge.scale() > 2
                || charge.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Pricing service returned an invalid charge");
        }

        Parcel parcel = new Parcel();
        parcel.setTrackingNumber(tracking.generate());
        parcel.setSenderId(userId);
        parcel.setCreatedBy(userId);
        parcel.setReceiverName(request.receiverName().trim());
        parcel.setReceiverPhone(request.receiverPhone().trim());
        parcel.setPickupAddress(request.pickupAddress().trim());
        parcel.setDeliveryAddress(request.deliveryAddress().trim());
        parcel.setWeight(request.weight());
        parcel.setSize(request.size().trim());
        parcel.setParcelType(request.parcelType().trim());
        parcel.setCharge(charge);
        parcel.setStatus(ParcelStatus.CREATED);

        Parcel saved = parcels.saveAndFlush(parcel);
        history.save(new ParcelStatusHistory(saved, null, ParcelStatus.CREATED,
                                             userId, "Parcel created"));
        // Event publishing is intentionally NOT implemented in Phase 2.
        // Add a transactional outbox before claiming reliable event delivery.
        return ParcelResponse.from(saved);
    }
}
