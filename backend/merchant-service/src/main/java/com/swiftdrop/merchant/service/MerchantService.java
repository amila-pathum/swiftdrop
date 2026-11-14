package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.client.MerchantParcelClient;
import com.swiftdrop.merchant.client.MerchantParcelDto;
import com.swiftdrop.merchant.client.MerchantParcelHistoryDto;
import com.swiftdrop.merchant.dto.request.MerchantRegistrationRequest;
import com.swiftdrop.merchant.dto.response.MerchantDashboardOrderDto;
import com.swiftdrop.merchant.dto.response.MerchantDashboardResponse;
import com.swiftdrop.merchant.dto.response.MerchantOrderStatusHistoryDto;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantParcelClient parcelClient;
    private final MerchantIdentityActivationClient identityActivation;

    public MerchantService(
            MerchantRepository merchantRepository,
            MerchantParcelClient parcelClient,
            MerchantIdentityActivationClient identityActivation
    ) {
        this.merchantRepository = merchantRepository;
        this.parcelClient = parcelClient;
        this.identityActivation = identityActivation;
    }

    public MerchantService(MerchantRepository merchantRepository, MerchantIdentityActivationClient identityActivation) {
        this(merchantRepository, null, identityActivation);
    }

    // Register a merchant application
    @Transactional
    public MerchantResponse registerMerchant(
            Long userId,
            MerchantRegistrationRequest request) {

        if (merchantRepository.existsByUserId(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "User already has a merchant application");
        }

        String registrationNumber =
                request.registrationNumber().trim();

        if (merchantRepository.existsByRegistrationNumber(
                registrationNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Business registration number already exists");
        }

        Merchant merchant = new Merchant();

        merchant.setUserId(userId);
        merchant.setBusinessName(request.businessName().trim());
        merchant.setRegistrationNumber(registrationNumber);
        merchant.setContactName(request.contactName().trim());
        merchant.setEmail(request.email().trim());
        merchant.setPhone(request.phone().trim());
        merchant.setAddress(request.address().trim());
        merchant.setStatus(MerchantStatus.PENDING);
        merchant.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        try {
            Merchant saved = merchantRepository.saveAndFlush(merchant);
            return MerchantResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Merchant application conflicts with an existing record");
        }
    }

    // Get logged-in user's merchant application
    @Transactional(readOnly = true)
    public MerchantResponse getMyMerchant(Long userId) {

        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Merchant application not found"));

        return MerchantResponse.from(merchant);
    }

    // Get merchant dashboard metrics and recent orders from parcel-service
    @Transactional(readOnly = true)
    public MerchantDashboardResponse getMyDashboard(Long userId) {
        Merchant merchant = approvedMerchant(userId);

        List<MerchantParcelDto> parcels = parcelClient.getParcelsByMerchant(userId, merchant.getId());
        if (parcels == null) {
            parcels = List.of();
        }

        long totalOrders = parcels.size();
        long pendingOrders = 0;
        long inTransitOrders = 0;
        long deliveredOrders = 0;

        Map<String, Long> ordersByStatus = new LinkedHashMap<>();

        for (MerchantParcelDto parcel : parcels) {
            String status = parcel.status() != null ? parcel.status().toUpperCase(Locale.ROOT) : "UNKNOWN";
            ordersByStatus.put(status, ordersByStatus.getOrDefault(status, 0L) + 1);

            switch (status) {
                case "CREATED", "PENDING", "PICKED_UP" -> pendingOrders++;
                case "IN_TRANSIT", "OUT_FOR_DELIVERY" -> inTransitOrders++;
                case "DELIVERED" -> deliveredOrders++;
            }
        }

        List<MerchantDashboardOrderDto> recentOrders = parcels.stream()
                .sorted(Comparator.comparing(MerchantParcelDto::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(p -> {
                    List<MerchantOrderStatusHistoryDto> history = List.of();
                    if (p.id() != null) {
                        List<MerchantParcelHistoryDto> rawHistory = parcelClient.getParcelHistory(p.id());
                        if (rawHistory != null) {
                            history = rawHistory.stream().map(h -> new MerchantOrderStatusHistoryDto(
                                    h.newStatus(),
                                    h.note(),
                                    h.changedAt() != null ? h.changedAt().toString() : null
                            )).toList();
                        }
                    }
                    return new MerchantDashboardOrderDto(
                            p.id(),
                            p.trackingNumber(),
                            p.receiverName() != null ? p.receiverName() : "Customer",
                            formatDestination(p.deliveryAddress()),
                            p.pickupAddress(),
                            p.deliveryAddress(),
                            p.status(),
                            p.createdAt() != null ? p.createdAt().toString() : null,
                            history
                    );
                })
                .toList();

        return new MerchantDashboardResponse(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getStatus().name(),
                totalOrders,
                pendingOrders,
                inTransitOrders,
                deliveredOrders,
                ordersByStatus,
                recentOrders
        );
    }

    private Merchant approvedMerchant(Long userId) {
        Merchant merchant = merchantRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Merchant application not found"));
        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Merchant must be approved to access dashboard data");
        }
        return merchant;
    }

    private String formatDestination(String deliveryAddress) {
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            return "Destination not specified";
        }
        String[] parts = deliveryAddress.split(",");
        if (parts.length >= 2) {
            return parts[parts.length - 2].trim() + ", " + parts[parts.length - 1].trim();
        }
        return deliveryAddress.trim();
    }

    // Admin: get all merchants or filter by status
    @Transactional(readOnly = true)
    public List<MerchantResponse> getAllMerchants(
            MerchantStatus status) {

        List<Merchant> merchants;

        if (status == null) {
            merchants = merchantRepository.findAll();
        } else {
            merchants = merchantRepository.findByStatus(status);
        }

        return merchants.stream()
                .map(MerchantResponse::from)
                .toList();
    }

    // Admin: get merchant by ID
    @Transactional(readOnly = true)
    public MerchantResponse getMerchantById(Long merchantId) {

        Merchant merchant = findMerchant(merchantId);

        return MerchantResponse.from(merchant);
    }

    // Admin: approve pending merchant
    @Transactional
    public MerchantResponse approveMerchant(
            Long merchantId,
            Long adminId,
            String authorization) {

        Merchant merchant = findForReview(merchantId);

        if (merchant.getStatus() != MerchantStatus.PENDING && merchant.getStatus() != MerchantStatus.APPROVED) {
            validatePending(merchant);
        }
        // Retry also repairs records approved before identity activation was integrated.
        // If the remote response is lost or our commit fails, retry reads ACTIVE and completes safely.
        identityActivation.ensureActive(merchant.getUserId(), authorization);
        if (merchant.getStatus() == MerchantStatus.APPROVED) return MerchantResponse.from(merchant);

        merchant.setStatus(MerchantStatus.APPROVED);
        merchant.setRejectionReason(null);
        merchant.setReviewedBy(adminId);
        merchant.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return MerchantResponse.from(
                merchantRepository.save(merchant));
    }

    // Admin: reject pending merchant
    @Transactional
    public MerchantResponse rejectMerchant(
            Long merchantId,
            Long adminId,
            String reason) {

        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required");
        }

        Merchant merchant = findForReview(merchantId);

        validatePending(merchant);

        merchant.setStatus(MerchantStatus.REJECTED);
        merchant.setRejectionReason(reason.trim());
        merchant.setReviewedBy(adminId);
        merchant.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return MerchantResponse.from(
                merchantRepository.save(merchant));
    }

    // Shared helper: find merchant
    private Merchant findForReview(Long merchantId) {
        return merchantRepository.findForReviewById(merchantId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found"));
    }

    private Merchant findMerchant(Long merchantId) {

        return merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Merchant not found"));
    }

    // Shared helper: only pending applications can be reviewed
    private void validatePending(Merchant merchant) {

        if (merchant.getStatus() != MerchantStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending merchant applications can be reviewed");
        }
    }
}
