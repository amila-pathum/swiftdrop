package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.ParcelDetailsResponse;
import com.swiftdrop.parcel.dto.response.ParcelHistoryResponse;
import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ParcelQueryService {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository historyRepository;

    public ParcelQueryService(
            ParcelRepository parcelRepository,
            ParcelStatusHistoryRepository historyRepository) {

        this.parcelRepository = parcelRepository;
        this.historyRepository = historyRepository;
    }

    // Get parcel details with ownership verification
    public ParcelDetailsResponse getParcelById(
            UUID parcelId,
            Long userId,
            String role) {

        Parcel parcel = getAuthorizedParcel(parcelId, userId, role);

        return ParcelDetailsResponse.from(parcel);
    }

    // Get status history with ownership verification
    public List<ParcelHistoryResponse> getParcelHistory(
            UUID parcelId,
            Long userId,
            String role) {

        Parcel parcel = getAuthorizedParcel(parcelId, userId, role);

        return historyRepository
                .findByParcelIdOrderByChangedAtAsc(parcel.getId())
                .stream()
                .map(ParcelHistoryResponse::from)
                .toList();
    }

    // Get authenticated customer's parcels
    public Page<ParcelDetailsResponse> getMyParcels(
            Long customerId,
            Pageable pageable) {

        if (customerId == null || customerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied"
            );
        }

        return parcelRepository
                .findBySenderId(customerId, pageable)
                .map(ParcelDetailsResponse::from);
    }

    public Page<ParcelDetailsResponse> getMerchantParcels(
            Long merchantId,
            Pageable pageable) {
        if (merchantId == null || merchantId <= 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return parcelRepository.findByMerchantId(merchantId, pageable)
                .map(ParcelDetailsResponse::from);
    }

    // List parcels by status - authorized staff only
    public Page<ParcelDetailsResponse> getParcelsByStatus(
            ParcelStatus status,
            String role,
            Pageable pageable) {

        if (!isStaff(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied"
            );
        }

        return parcelRepository
                .findByStatus(status, pageable)
                .map(ParcelDetailsResponse::from);
    }

    // Shared authorization helper
    private Parcel getAuthorizedParcel(
            UUID parcelId,
            Long userId,
            String role) {

        if (userId == null || userId <= 0 || role == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied"
            );
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Parcel not found"
                ));

        if (isStaff(role)) {
            return parcel;
        }

        if ("CUSTOMER".equals(role)
                && userId.equals(parcel.getSenderId())) {
            return parcel;
        }

        if ("MERCHANT".equals(role)
                && userId.equals(parcel.getMerchantId())) {
            return parcel;
        }

        // Do not expose whether another user's parcel exists.
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Parcel not found"
        );
    }

    private boolean isStaff(String role) {
        return "ADMIN".equals(role)
                || "DISPATCHER".equals(role);
    }
}
