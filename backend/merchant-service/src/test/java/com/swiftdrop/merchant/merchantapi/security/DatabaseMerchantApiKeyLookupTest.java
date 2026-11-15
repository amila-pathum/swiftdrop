package com.swiftdrop.merchant.merchantapi.security;

import com.swiftdrop.merchant.entity.Merchant;
import com.swiftdrop.merchant.entity.MerchantApiKey;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.repository.MerchantApiKeyRepository;
import com.swiftdrop.merchant.service.MerchantApiKeyService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseMerchantApiKeyLookupTest {

    private final MerchantApiKeyRepository keys =
            mock(MerchantApiKeyRepository.class);

    private final DatabaseMerchantApiKeyLookup lookup =
            new DatabaseMerchantApiKeyLookup(keys);

    @Test
    void missingKeysDoNotQueryDatabase() {
        assertTrue(lookup.findByApiKey(null).isEmpty());
        assertTrue(lookup.findByApiKey(" ").isEmpty());

        verifyNoInteractions(keys);
    }

    @Test
    void unknownOrRevokedKeyReturnsEmpty() {
        String hash = MerchantApiKeyService.sha256("unknown-key");

        when(keys.findByKeyHashAndRevokedAtIsNull(hash))
                .thenReturn(Optional.empty());

        assertTrue(lookup.findByApiKey("unknown-key").isEmpty());
    }

    @Test
    void approvedMerchantReturnsIdentity() {
        stubKey("issued-key", MerchantStatus.APPROVED);

        assertEquals(
                new MerchantKeyDetails("9", true),
                lookup.findByApiKey("issued-key").orElseThrow()
        );

        verify(keys).findByKeyHashAndRevokedAtIsNull(
                MerchantApiKeyService.sha256("issued-key")
        );
    }

    @Test
    void unapprovedMerchantsAreMarkedUnapproved() {
        for (MerchantStatus status :
                new MerchantStatus[]{
                        MerchantStatus.PENDING,
                        MerchantStatus.REJECTED
                }) {
            stubKey("issued-key", status);

            assertEquals(
                    new MerchantKeyDetails("9", false),
                    lookup.findByApiKey("issued-key").orElseThrow()
            );
        }
    }

    private void stubKey(String plaintext, MerchantStatus status) {
        Merchant merchant = new Merchant();
        merchant.setId(9L);
        merchant.setStatus(status);

        MerchantApiKey key = new MerchantApiKey();
        key.setMerchant(merchant);

        when(keys.findByKeyHashAndRevokedAtIsNull(
                MerchantApiKeyService.sha256(plaintext)
        )).thenReturn(Optional.of(key));
    }
}