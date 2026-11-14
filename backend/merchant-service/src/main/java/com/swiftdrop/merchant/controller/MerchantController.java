package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.dto.request.MerchantRegistrationRequest;
import com.swiftdrop.merchant.dto.request.RejectMerchantRequest;
import com.swiftdrop.merchant.dto.response.MerchantDashboardResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.service.MerchantService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    // Merchant application registration
    @PostMapping("/register")
    public ResponseEntity<MerchantResponse> registerMerchant(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MerchantRegistrationRequest request) {

        requireApplicantRole(jwt);

        Long userId = getAuthenticatedUserId(jwt);

        MerchantResponse response =
                merchantService.registerMerchant(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    // Get current user's merchant profile
    @GetMapping("/me")
    public ResponseEntity<MerchantResponse> getMyMerchant(
            @AuthenticationPrincipal Jwt jwt) {

        requireApplicantRole(jwt);

        Long userId = getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                merchantService.getMyMerchant(userId));
    }

    // Get current user's merchant dashboard data
    @GetMapping("/me/dashboard")
    public ResponseEntity<MerchantDashboardResponse> getMyDashboard(
            @AuthenticationPrincipal Jwt jwt) {

        requireApplicantRole(jwt);

        Long userId = getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                merchantService.getMyDashboard(userId));
    }

    // Admin: list merchant applications
    @GetMapping("/admin")
    public ResponseEntity<List<MerchantResponse>> getAllMerchants(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) MerchantStatus status) {

        requireAdmin(jwt);

        return ResponseEntity.ok(
                merchantService.getAllMerchants(status));
    }

    // Admin: view a merchant
    @GetMapping("/admin/{merchantId}")
    public ResponseEntity<MerchantResponse> getMerchantById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long merchantId) {

        requireAdmin(jwt);

        return ResponseEntity.ok(
                merchantService.getMerchantById(merchantId));
    }

    // Admin: approve merchant
    @PatchMapping("/admin/{merchantId}/approve")
    public ResponseEntity<MerchantResponse> approveMerchant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long merchantId) {

        requireAdmin(jwt);

        Long adminId = getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                merchantService.approveMerchant(merchantId, adminId));
    }

    // Admin: reject merchant
    @PatchMapping("/admin/{merchantId}/reject")
    public ResponseEntity<MerchantResponse> rejectMerchant(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long merchantId,
            @Valid @RequestBody RejectMerchantRequest request) {

        requireAdmin(jwt);

        Long adminId = getAuthenticatedUserId(jwt);

        return ResponseEntity.ok(
                merchantService.rejectMerchant(
                        merchantId,
                        adminId,
                        request.reason()));
    }

    // Extract Identity Service user ID from JWT subject
    private Long getAuthenticatedUserId(Jwt jwt) {

        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid JWT subject");
        }
    }

    // Only ADMIN can access admin endpoints
    private void requireAdmin(Jwt jwt) {

        if (jwt == null ||
                !"ADMIN".equals(jwt.getClaimAsString("role"))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin access required");
        }
    }

    // Customer can apply; approved merchants can access profile
    private void requireApplicantRole(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required");
        }

        String role = jwt.getClaimAsString("role");

        if (!"CUSTOMER".equals(role) &&
                !"MERCHANT".equals(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Customer or merchant access required");
        }
    }
}