package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.PublicTrackingResponse;
import com.swiftdrop.parcel.dto.response.PublicTrackingResponse.TrackingEntry;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
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
        // No public location, ETA or description is stored in the current schema.
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

    /** Records the old and new status in the same transaction as the update. */
    @Transactional
    public Parcel changeStatus(UUID parcelId, ParcelStatus newStatus, UUID changedBy, String note) {
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
