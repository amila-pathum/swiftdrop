package com.swiftdrop.parcel.service;

import com.swiftdrop.parcel.dto.response.ParcelDetailsResponse;
import com.swiftdrop.parcel.dto.response.ParcelHistoryResponse;
import com.swiftdrop.parcel.dto.response.ParcelSummaryResponse;
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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
        return getMyParcels(customerId, null, pageable);
    }

    // Get authenticated customer's parcels, optionally only those in one status
    public Page<ParcelDetailsResponse> getMyParcels(
            Long customerId,
            ParcelStatus status,
            Pageable pageable) {

        requireCustomer(customerId);

        Page<Parcel> page = status == null
                ? parcelRepository.findBySenderId(customerId, pageable)
                : parcelRepository.findBySenderIdAndStatus(customerId, status, pageable);
        return page.map(ParcelDetailsResponse::from);
    }

    // Count the authenticated customer's parcels in each status
    public ParcelSummaryResponse getMyParcelSummary(Long customerId) {
        requireCustomer(customerId);

        Map<ParcelStatus, Long> byStatus = new EnumMap<>(ParcelStatus.class);
        for (ParcelStatus status : ParcelStatus.values()) {
            byStatus.put(status, 0L);
        }
        long total = 0;
        for (Object[] row : parcelRepository.countBySenderIdGroupedByStatus(customerId)) {
            long count = ((Number) row[1]).longValue();
            byStatus.put((ParcelStatus) row[0], count);
            total += count;
        }
        return new ParcelSummaryResponse(total, byStatus);
    }

    private void requireCustomer(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Access denied"
            );
        }
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
