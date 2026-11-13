package com.swiftdrop.parcel.service;

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
