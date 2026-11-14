package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.client.MerchantParcelClient;
import com.swiftdrop.merchant.client.MerchantParcelDto;
import com.swiftdrop.merchant.client.MerchantParcelHistoryDto;
import com.swiftdrop.merchant.dto.response.CreatedMerchantApiKeyResponse;
import com.swiftdrop.merchant.dto.response.MerchantApiKeyResponse;
import com.swiftdrop.merchant.dto.response.MerchantDashboardResponse;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiKey;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.merchantapi.security.MerchantKeyDetails;
import com.swiftdrop.merchant.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceWorkGuideTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private MerchantApiKeyRepository apiKeyRepository;

    @Mock
    private MerchantParcelClient parcelClient;

    private MerchantService merchantService;
    private MerchantApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, parcelClient);
        apiKeyService = new MerchantApiKeyService(merchantRepository, apiKeyRepository);
    }

    @Test
    @DisplayName("Work Guide Test 1: A PENDING or REJECTED merchant cannot create an API key (403)")
    void pendingOrRejectedMerchantCannotCreateApiKey() {
        Merchant pendingMerchant = new Merchant();
        pendingMerchant.setId(10L);
        pendingMerchant.setUserId(100L);
        pendingMerchant.setStatus(MerchantStatus.PENDING);

        when(merchantRepository.findByUserId(100L)).thenReturn(Optional.of(pendingMerchant));

        assertThatThrownBy(() -> apiKeyService.create(100L, "Storefront"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Merchant must be approved to manage API keys");
                });

        Merchant rejectedMerchant = new Merchant();
        rejectedMerchant.setId(11L);
        rejectedMerchant.setUserId(101L);
        rejectedMerchant.setStatus(MerchantStatus.REJECTED);

        when(merchantRepository.findByUserId(101L)).thenReturn(Optional.of(rejectedMerchant));

        assertThatThrownBy(() -> apiKeyService.create(101L, "Storefront"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(apiKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Work Guide Test 2: Admin approval updates status to APPROVED, rejection needs a reason")
    void adminReviewRequiresReasonAndUpdatesStatus() {
        Merchant pendingMerchant = new Merchant();
        pendingMerchant.setId(20L);
        pendingMerchant.setStatus(MerchantStatus.PENDING);
        pendingMerchant.setBusinessName("Test Corp");

        when(merchantRepository.findById(20L)).thenReturn(Optional.of(pendingMerchant));
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(inv -> inv.getArgument(0));

        // Rejection without reason fails with 400 Bad Request
        assertThatThrownBy(() -> merchantService.rejectMerchant(20L, 999L, ""))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(rse.getReason()).contains("Rejection reason is required");
                });

        assertThatThrownBy(() -> merchantService.rejectMerchant(20L, 999L, "   "))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });

        // Rejection with reason succeeds
        MerchantResponse rejected = merchantService.rejectMerchant(20L, 999L, "Incomplete documentation");
        assertThat(rejected.status()).isEqualTo(MerchantStatus.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Incomplete documentation");

        // Approval of a pending merchant succeeds
        pendingMerchant.setStatus(MerchantStatus.PENDING);
        MerchantResponse approved = merchantService.approveMerchant(20L, 999L);
        assertThat(approved.status()).isEqualTo(MerchantStatus.APPROVED);
        assertThat(pendingMerchant.getReviewedBy()).isEqualTo(999L);
        assertThat(pendingMerchant.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("Work Guide Test 3: The API key is stored hashed and the plain key is never returned again")
    void apiKeyStoredHashedAndPlainKeyNeverReturnedAgain() {
        Merchant approvedMerchant = new Merchant();
        approvedMerchant.setId(30L);
        approvedMerchant.setUserId(300L);
        approvedMerchant.setStatus(MerchantStatus.APPROVED);

        when(merchantRepository.findByUserId(300L)).thenReturn(Optional.of(approvedMerchant));

        ArgumentCaptor<MerchantApiKey> keyCaptor = ArgumentCaptor.forClass(MerchantApiKey.class);
        when(apiKeyRepository.saveAndFlush(keyCaptor.capture())).thenAnswer(inv -> {
            MerchantApiKey k = inv.getArgument(0);
            k.setId(1L);
            return k;
        });

        CreatedMerchantApiKeyResponse created = apiKeyService.create(300L, "Warehouse Key");

        // The plaintext key starts with sd_
        assertThat(created.secretKey()).startsWith("sd_");
        MerchantApiKey captured = keyCaptor.getValue();

        // Plaintext is NOT stored in DB entity, only SHA-256 hash
        assertThat(captured.getKeyHash()).isNotEqualTo(created.secretKey());
        assertThat(captured.getKeyHash()).isEqualTo(MerchantApiKeyService.sha256(created.secretKey()));
        assertThat(captured.getKeyPrefix()).isEqualTo(created.secretKey().substring(0, 12));

        // When listing existing keys, only prefix is returned, plain key is never present
        when(apiKeyRepository.findByMerchantIdOrderByCreatedAtDesc(30L)).thenReturn(List.of(captured));
        List<MerchantApiKeyResponse> listed = apiKeyService.list(300L);
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).prefix()).isEqualTo(captured.getKeyPrefix());
    }

    @Test
    @DisplayName("Work Guide Test 4: A revoked key fails the key check")
    void revokedKeyFailsKeyCheck() {
        String rawKey = "sd_test_secret_key_1234567890123456";
        String hash = MerchantApiKeyService.sha256(rawKey);

        Merchant merchant = new Merchant();
        merchant.setId(40L);
        merchant.setStatus(MerchantStatus.APPROVED);

        MerchantApiKey activeKey = new MerchantApiKey();
        activeKey.setId(5L);
        activeKey.setMerchant(merchant);
        activeKey.setKeyHash(hash);
        activeKey.setRevokedAt(null);

        // Active key succeeds lookup
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)).thenReturn(Optional.of(activeKey));
        Optional<MerchantKeyDetails> lookupResult = apiKeyService.findByApiKey(rawKey);
        assertThat(lookupResult).isPresent();
        assertThat(lookupResult.get().merchantId()).isEqualTo("40");
        assertThat(lookupResult.get().approved()).isTrue();
        assertThat(activeKey.getLastUsedAt()).isNotNull();

        // Revoked key fails lookup
        when(apiKeyRepository.findByKeyHashAndRevokedAtIsNull(hash)).thenReturn(Optional.empty());
        Optional<MerchantKeyDetails> revokedResult = apiKeyService.findByApiKey(rawKey);
        assertThat(revokedResult).isEmpty();
    }

    @Test
    @DisplayName("Work Guide Test 5: A merchant sees only their own orders on the dashboard and PENDING gets 403")
    void merchantSeesOnlyTheirOwnOrdersOnDashboard() {
        // Pending merchant gets 403 Forbidden
        Merchant pendingMerchant = new Merchant();
        pendingMerchant.setId(50L);
        pendingMerchant.setUserId(500L);
        pendingMerchant.setStatus(MerchantStatus.PENDING);

        when(merchantRepository.findByUserId(500L)).thenReturn(Optional.of(pendingMerchant));

        assertThatThrownBy(() -> merchantService.getMyDashboard(500L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).contains("Merchant must be approved to access dashboard data");
                });

        // Approved merchant receives dashboard data filtered for their merchant
        Merchant approvedMerchant = new Merchant();
        approvedMerchant.setId(51L);
        approvedMerchant.setUserId(501L);
        approvedMerchant.setBusinessName("UrbanNest Commerce");
        approvedMerchant.setStatus(MerchantStatus.APPROVED);

        when(merchantRepository.findByUserId(501L)).thenReturn(Optional.of(approvedMerchant));

        MerchantParcelDto p1 = new MerchantParcelDto(
                "parcel-1", "SD-20261112-0841", 501L, "Olivia Bennett", "+1555123456",
                "10 Warehouse St", "Austin, TX", "IN_TRANSIT", "51", "REF-001", Instant.now()
        );
        MerchantParcelDto p2 = new MerchantParcelDto(
                "parcel-2", "SD-20261112-0840", 501L, "James Wilson", "+1555654321",
                "10 Warehouse St", "Seattle, WA", "PENDING", "51", "REF-002", Instant.now().minusSeconds(3600)
        );
        MerchantParcelDto p3 = new MerchantParcelDto(
                "parcel-3", "SD-20261111-0839", 501L, "Emma Davis", "+1555987654",
                "10 Warehouse St", "Los Angeles, CA", "DELIVERED", "51", "REF-003", Instant.now().minusSeconds(7200)
        );

        when(parcelClient.getParcelsByMerchant(501L, 51L)).thenReturn(List.of(p1, p2, p3));
        when(parcelClient.getParcelHistory("parcel-1")).thenReturn(List.of(
                new MerchantParcelHistoryDto("h1", "CREATED", "IN_TRANSIT", Instant.now(), "Dispatched")
        ));

        MerchantDashboardResponse dashboard = merchantService.getMyDashboard(501L);

        assertThat(dashboard.merchantId()).isEqualTo(51L);
        assertThat(dashboard.businessName()).isEqualTo("UrbanNest Commerce");
        assertThat(dashboard.totalOrders()).isEqualTo(3);
        assertThat(dashboard.pendingOrders()).isEqualTo(1);
        assertThat(dashboard.inTransitOrders()).isEqualTo(1);
        assertThat(dashboard.deliveredOrders()).isEqualTo(1);
        assertThat(dashboard.recentOrders()).hasSize(3);
        assertThat(dashboard.recentOrders().get(0).trackingNumber()).isEqualTo("SD-20261112-0841");
        assertThat(dashboard.recentOrders().get(0).statusHistory()).hasSize(1);
    }
}