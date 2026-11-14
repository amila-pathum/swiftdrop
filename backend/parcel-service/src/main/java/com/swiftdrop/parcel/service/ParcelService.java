package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.ParcelReceipt;
import com.swiftdrop.parcel.dto.response.PublicTrackingResponse;
import com.swiftdrop.parcel.dto.response.PublicTrackingResponse.TrackingEntry;
import org.springframework.transaction.annotation.Isolation;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ParcelService {
    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;
    private final ParcelStatusTransitionValidator transitions;

    public ParcelService(ParcelRepository parcels, ParcelStatusHistoryRepository history,
                         ParcelStatusTransitionValidator transitions) {
        this.parcels = parcels;
        this.history = history;
        this.transitions = transitions;
    }

    @Transactional
    public Parcel assignCourier(UUID parcelId, Long courierUserId) {
        if (courierUserId == null || courierUserId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid courier user identifier");
        }
        Parcel parcel = parcels.findForStatusUpdateById(parcelId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));
        if (parcel.getStatus() == ParcelStatus.DELIVERED || parcel.getStatus() == ParcelStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot assign a closed parcel");
        }
        parcel.setAssignedCourierId(courierUserId);
        return parcel;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PublicTrackingResponse track(String trackingNumber) {
        if (trackingNumber == null || !trackingNumber.matches("[A-Za-z0-9-]{6,40}")) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parcel not found");
        }
        Parcel parcel = parcels.findByTrackingNumber(trackingNumber).orElseThrow(() ->
            new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parcel not found"));
        var entries = history.findByParcelIdOrderByChangedAtAsc(parcel.getId()).stream()
            .map(entry -> new TrackingEntry(
                entry.getNewStatus(), null, null, entry.getChangedAt()))
            .toList();
        return new PublicTrackingResponse(
            parcel.getTrackingNumber(), parcel.getStatus(), parcel.getCreatedAt(),
            null, null, null, entries);
    }

    /** Saves a new order and its initial history together. */
    @Transactional
    public Parcel createParcel(Parcel parcel) {
        Objects.requireNonNull(parcel, "parcel is required");
        if (parcel.getId() != null) {
            throw new IllegalArgumentException("A new parcel must not already have an ID");
        }
        Objects.requireNonNull(parcel.getCreatedBy(), "createdBy is required");
        parcel.setStatus(ParcelStatus.CREATED);
        Parcel saved = parcels.save(parcel);
        history.save(new ParcelStatusHistory(saved, null, ParcelStatus.CREATED,
                saved.getCreatedBy(), "Parcel created"));
        return saved;
    }

    public List<Parcel> listParcels(ParcelStatus status) {
        return status == null ? parcels.findAll()
                : parcels.findByStatusOrderByCreatedAtAsc(status);
    }

    public Optional<Parcel> findParcel(UUID parcelId) {
        return parcels.findById(parcelId);
    }

    /** Returns all parcels created by the given customer, newest first. */
    @Transactional(readOnly = true)
    public List<Parcel> getMyParcels(Long senderId) {
        Objects.requireNonNull(senderId, "senderId is required");
        return parcels.findBySenderIdOrderByCreatedAtDesc(senderId);
    }

    /**
     * Cancels a parcel owned by the customer, if it is still in CREATED status.
     */
    @Transactional
    public Parcel cancelParcel(UUID parcelId, Long customerId) {
        Objects.requireNonNull(parcelId, "parcelId is required");
        Objects.requireNonNull(customerId, "customerId is required");
        Parcel parcel = parcels.findForStatusUpdateById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));

        if (!customerId.equals(parcel.getSenderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is denied");
        }

        if (parcel.getStatus() != ParcelStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only parcels with CREATED status can be cancelled");
        }

        return changeStatus(parcelId, ParcelStatus.CANCELLED, customerId, "Cancelled by customer");
    }

    /**
     * Generates a downloadable PDF receipt for a delivered parcel owned by the customer.
     */
    @Transactional(readOnly = true)
    public ParcelReceipt generateReceipt(UUID parcelId, Long customerId) {
        Objects.requireNonNull(parcelId, "parcelId is required");
        Objects.requireNonNull(customerId, "customerId is required");

        Parcel parcel = parcels.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));

        if (!customerId.equals(parcel.getSenderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is denied");
        }

        if (parcel.getStatus() != ParcelStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Receipt is only available for delivered parcels");
        }

        Instant deliveredAt = history.findByParcelIdOrderByChangedAtAsc(parcelId).stream()
                .filter(h -> h.getNewStatus() == ParcelStatus.DELIVERED)
                .map(ParcelStatusHistory::getChangedAt)
                .reduce((first, second) -> second)
                .orElse(parcel.getUpdatedAt());

        byte[] pdfContent = PdfReceiptGenerator.generate(parcel, deliveredAt);
        String filename = "receipt-" + parcel.getTrackingNumber() + ".pdf";

        return new ParcelReceipt(filename, pdfContent);
    }

    /** Records the old and new status in the same transaction as the update. */
    @Transactional
    public Parcel changeStatus(UUID parcelId, ParcelStatus newStatus, Long changedBy, String note) {
        Objects.requireNonNull(changedBy, "changedBy is required");
        Parcel parcel = parcels.findForStatusUpdateById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Parcel not found: " + parcelId));
        ParcelStatus previousStatus = parcel.getStatus();
        transitions.validate(previousStatus, newStatus);
        parcel.setStatus(newStatus);
        history.save(new ParcelStatusHistory(parcel, previousStatus, newStatus, changedBy, note));
        return parcel;
    }
}
