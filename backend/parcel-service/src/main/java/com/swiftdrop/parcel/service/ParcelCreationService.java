package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.dto.response.ParcelResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.event.ParcelEventOutbox;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;

@Service
public class ParcelCreationService {
    private static final int MAX_TRACKING_ATTEMPTS = 10;
    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;
    private final PricingClient pricing;
    private final TrackingNumberGenerator tracking;
    private final ParcelEventOutbox outbox;

    public ParcelCreationService(ParcelRepository parcels,
                                 ParcelStatusHistoryRepository history,
                                 PricingClient pricing,
                                 TrackingNumberGenerator tracking,
                                 ParcelEventOutbox outbox) {
        this.parcels = parcels;
        this.history = history;
        this.pricing = pricing;
        this.tracking = tracking;
        this.outbox = outbox;
    }

    @Transactional
    public ParcelResponse createCustomerParcel(CreateParcelRequest request, Long userId,
                                                String accessToken) {
        return create(request, userId, null, accessToken, null);
    }

    @Transactional
    public ParcelResponse createMerchantParcel(CreateParcelRequest request, Long userId,
                                                String accessToken) {
        return create(request, userId, userId, accessToken, null);
    }

    @Transactional
    public ParcelResponse createMerchantIntegrationParcel(CreateParcelRequest request, Long userId,
            String accessToken, String reference) {
        return create(request, userId, userId, accessToken, reference);
    }

    private ParcelResponse create(CreateParcelRequest request, Long userId, Long merchantId,
                                  String accessToken, String reference) {
        if (userId == null || userId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated user identifier");
        }
        // Quote before writing to the database; never trust a client-supplied charge.
        BigDecimal charge = pricing.quote(request, accessToken);
        if (charge == null || charge.signum() < 0 || charge.scale() > 2
                || charge.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Pricing service returned an invalid charge");
        }

        Parcel parcel = new Parcel();
        parcel.setSenderId(userId);
        parcel.setCreatedBy(userId);
        parcel.setMerchantId(merchantId);
        parcel.setMerchantOrderReference(reference);
        parcel.setReceiverName(request.receiverName().trim());
        parcel.setReceiverPhone(request.receiverPhone().trim());
        parcel.setPickupAddress(formatAddress(request.pickupAddressLine1(),
                                              request.pickupAddressLine2(),
                                              request.pickupCity()));
        parcel.setDeliveryAddress(formatAddress(request.deliveryAddressLine1(),
                                                request.deliveryAddressLine2(),
                                                request.deliveryCity()));
        parcel.setWeight(request.weight());
        parcel.setSize(request.size().trim());
        parcel.setParcelType(request.parcelType().trim());
        parcel.setCharge(charge);
        parcel.setStatus(ParcelStatus.CREATED);

        parcel.setId(java.util.UUID.randomUUID());
        parcel.setCreatedAt(java.time.Instant.now());
        parcel.setUpdatedAt(parcel.getCreatedAt());
        Parcel saved = insertWithUniqueTrackingNumber(parcel);
        ParcelStatusHistory entry = new ParcelStatusHistory(
            saved, null, ParcelStatus.CREATED, userId, "Parcel created");
        history.save(entry);
        outbox.enqueueStatusChanged(saved, null, ParcelStatus.CREATED, entry.getChangedAt());
        return ParcelResponse.from(saved);
    }

    private Parcel insertWithUniqueTrackingNumber(Parcel parcel) {
        for (int attempt = 0; attempt < MAX_TRACKING_ATTEMPTS; attempt++) {
            parcel.setTrackingNumber(tracking.generate());
            if (parcels.insertIfTrackingNumberAvailable(parcel) == 1) {
                return parcels.findById(parcel.getId()).orElseThrow();
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Unable to allocate a tracking number. Please try again.");
    }

    private String formatAddress(String line1, String line2, String city) {
        if (line2 == null || line2.isBlank()) {
            return line1.trim() + ", " + city.trim();
        }
        return line1.trim() + ", " + line2.trim() + ", " + city.trim();
    }
}
