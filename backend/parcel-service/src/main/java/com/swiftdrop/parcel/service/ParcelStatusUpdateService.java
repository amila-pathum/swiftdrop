package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.request.UpdateParcelStatusRequest;
import com.swiftdrop.parcel.dto.response.ParcelStatusUpdateResponse;
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
import java.util.UUID;

@Service
public class ParcelStatusUpdateService {
    private final ParcelRepository parcels;
    private final ParcelStatusHistoryRepository history;
    private final ParcelStatusTransitionValidator transitions;
    private final CourierAssignmentVerifier courierAssignments;

    public ParcelStatusUpdateService(ParcelRepository parcels,
            ParcelStatusHistoryRepository history,
            ParcelStatusTransitionValidator transitions,
            CourierAssignmentVerifier courierAssignments) {
        this.parcels = parcels;
        this.history = history;
        this.transitions = transitions;
        this.courierAssignments = courierAssignments;
    }

    @Transactional
    public ParcelStatusUpdateResponse update(UUID parcelId, UpdateParcelStatusRequest request,
                                              Long actorId, String role) {
        if (actorId == null || actorId <= 0 || role == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is denied");
        }
        // Pessimistic lock ensures concurrent updates observe the latest status.
        Parcel parcel = parcels.findForStatusUpdateById(parcelId).orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcel not found"));

        boolean authorized = switch (role) {
            case "ADMIN", "DISPATCHER" -> true;
            case "COURIER" -> courierAssignments.isAssigned(parcel, actorId);
            default -> false;
        };
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access is denied");
        }
        // Member 5 owns cancellation; prevent this status endpoint bypassing that flow.
        if (request.status() == ParcelStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the cancellation flow");
        }

        ParcelStatus previous = parcel.getStatus();
        if (!transitions.isAllowed(previous, request.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Invalid parcel status transition: " + previous + " -> " + request.status());
        }
        String note = request.note() == null ? null : request.note().trim();
        parcel.setStatus(request.status());
        ParcelStatusHistory entry = new ParcelStatusHistory(parcel, previous, request.status(), actorId, note);
        history.saveAndFlush(entry);
        return new ParcelStatusUpdateResponse(parcelId, previous, request.status(), entry.getChangedAt());
    }
}
