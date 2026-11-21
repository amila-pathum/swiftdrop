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
import com.swiftdrop.parcel.event.ParcelEventOutbox;
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
    private final ParcelEventOutbox outbox;

    public ParcelService(ParcelRepository parcels, ParcelStatusHistoryRepository history,
                         ParcelStatusTransitionValidator transitions,
                         ParcelEventOutbox outbox) {
        this.parcels = parcels;
        this.history = history;
        this.transitions = transitions;
        this.outbox = outbox;
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
        // The Courier Service keeps its own copy of the delivery details; without
        // this the courier sees only the tracking number and status.
        outbox.enqueueDeliverySnapshot(parcel, Instant.now());
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
            .map(entry -> new TrackingEntry(entry.getNewStatus(), entry.getChangedAt()))
            .toList();
        return new PublicTrackingResponse(
            parcel.getTrackingNumber(), parcel.getStatus(), parcel.getCreatedAt(),
            SriLankaDistricts.districtOf(parcel.getDeliveryAddress()).orElse(null), entries);
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
        Parcel saved = parcels.saveAndFlush(parcel);
        ParcelStatusHistory entry = new ParcelStatusHistory(saved, null,
                ParcelStatus.CREATED, saved.getCreatedBy(), "Parcel created");
        history.save(entry);
        outbox.enqueueStatusChanged(saved, null, ParcelStatus.CREATED, entry.getChangedAt());
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

        // The lifecycle rules decide whether a parcel can still be cancelled
        // (today: only from CREATED); this flow does not keep its own copy.
        if (!transitions.isAllowed(parcel.getStatus(), ParcelStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
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

    /**
     * Applies a status the assigned courier confirmed in the Courier Service
     * (event courier.delivery-status-changed.v1). Returns false, changing nothing,
     * when the parcel is unknown, assigned to someone else, already has this
     * status (a redelivered event) or cannot move to it.
     */
    @Transactional
    public boolean applyCourierStatus(UUID parcelId, Long courierUserId, ParcelStatus newStatus, UUID submissionId) {
        Objects.requireNonNull(newStatus, "newStatus is required");
        Parcel parcel = parcels.findForStatusUpdateById(parcelId).orElse(null);
        if (parcel == null || courierUserId == null || !courierUserId.equals(parcel.getAssignedCourierId())) {
            return false;
        }
        if (parcel.getStatus() == newStatus || !transitions.isAllowed(parcel.getStatus(), newStatus)) {
            return false;
        }
        changeStatus(parcelId, newStatus, courierUserId, "Updated by courier", submissionId);
        return true;
    }

    /** Records the old and new status in the same transaction as the update. */
    @Transactional
    public Parcel changeStatus(UUID parcelId, ParcelStatus newStatus, Long changedBy, String note) {
        return changeStatus(parcelId, newStatus, changedBy, note, null);
    }

    private Parcel changeStatus(UUID parcelId, ParcelStatus newStatus, Long changedBy, String note, UUID eventId) {
        Objects.requireNonNull(changedBy, "changedBy is required");
        Parcel parcel = parcels.findForStatusUpdateById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Parcel not found: " + parcelId));
        ParcelStatus previousStatus = parcel.getStatus();
        transitions.validate(previousStatus, newStatus);
        parcel.setStatus(newStatus);
        ParcelStatusHistory entry = new ParcelStatusHistory(
            parcel, previousStatus, newStatus, changedBy, note);
        history.save(entry);
        if (eventId == null) {
            outbox.enqueueStatusChanged(parcel, previousStatus, newStatus, entry.getChangedAt());
        } else {
            outbox.enqueueStatusChanged(eventId, parcel, previousStatus, newStatus, entry.getChangedAt());
        }
        return parcel;
    }
}
