package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.dto.request.MerchantRegistrationRequest;
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
import java.util.List;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
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
            Long adminId) {

        Merchant merchant = findMerchant(merchantId);

        validatePending(merchant);

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

        Merchant merchant = findMerchant(merchantId);

        validatePending(merchant);

        merchant.setStatus(MerchantStatus.REJECTED);
        merchant.setRejectionReason(reason.trim());
        merchant.setReviewedBy(adminId);
        merchant.setReviewedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return MerchantResponse.from(
                merchantRepository.save(merchant));
    }

    // Shared helper: find merchant
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
