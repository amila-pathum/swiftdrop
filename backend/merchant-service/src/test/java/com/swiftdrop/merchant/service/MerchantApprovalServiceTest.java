package com.swiftdrop.merchant.service;

import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.repository.MerchantRepository;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantApprovalServiceTest {
    MerchantRepository repository;
    MerchantIdentityActivationClient identity;
    MerchantService service;
    Merchant merchant;
    @BeforeEach void setup() {
        repository = mock(MerchantRepository.class); identity = mock(MerchantIdentityActivationClient.class);
        service = new MerchantService(repository, identity);
        merchant = new Merchant(); merchant.setId(2L); merchant.setUserId(20L); merchant.setStatus(MerchantStatus.PENDING);
        when(repository.findForReviewById(2L)).thenReturn(Optional.of(merchant));
        when(repository.save(merchant)).thenReturn(merchant);
    }
    @Test void confirmsActivationBeforeSavingBusinessApproval() {
        assertEquals(MerchantStatus.APPROVED, service.approveMerchant(2L, 99L, "Bearer admin").status());
        var order = inOrder(identity, repository);
        order.verify(identity).ensureActive(20L, "Bearer admin"); order.verify(repository).save(merchant);
        assertEquals(99L, merchant.getReviewedBy());
    }
    @Test void failedActivationDoesNotMarkTheBusinessApproved() {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(identity).ensureActive(20L, "Bearer admin");
        assertThrows(ResponseStatusException.class, () -> service.approveMerchant(2L, 99L, "Bearer admin"));
        assertEquals(MerchantStatus.PENDING, merchant.getStatus());
        verify(repository, never()).save(any());
    }
    @Test void previouslyApprovedBusinessCanRepairPendingIdentityWithoutRewritingTheReview() {
        merchant.setStatus(MerchantStatus.APPROVED); merchant.setReviewedBy(88L);
        assertEquals(MerchantStatus.APPROVED, service.approveMerchant(2L, 99L, "Bearer admin").status());
        verify(identity).ensureActive(20L, "Bearer admin"); verify(repository, never()).save(any());
        assertEquals(88L, merchant.getReviewedBy());
    }
    @Test void rejectedBusinessCannotActivateItsOwner() {
        merchant.setStatus(MerchantStatus.REJECTED);
        assertThrows(ResponseStatusException.class, () -> service.approveMerchant(2L, 99L, "Bearer admin"));
        verifyNoInteractions(identity);
    }
}
