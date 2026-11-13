package com.swiftdrop.merchant.merchantapi.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantApiKeyAuthenticatorTest {

    @Mock
    private MerchantApiKeyLookup keyLookup;

    private MerchantApiKeyAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new MerchantApiKeyAuthenticator(keyLookup);
    }

    @Test
    void missingKeyIsRejected() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.authenticate(null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(keyLookup);
    }

    @Test
    void blankKeyIsRejected() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.authenticate("   ")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(keyLookup);
    }

    @Test
    void invalidOrRevokedKeyIsRejected() {
        when(keyLookup.findByApiKey("invalid-test-key"))
                .thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.authenticate("invalid-test-key")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
    }

    @Test
    void unapprovedMerchantIsRejected() {
        when(keyLookup.findByApiKey("pending-test-key"))
                .thenReturn(Optional.of(
                        new MerchantKeyDetails("merchant-1", false)
                ));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> authenticator.authenticate("pending-test-key")
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void approvedMerchantIsAuthenticated() {
        MerchantKeyDetails merchant =
                new MerchantKeyDetails("merchant-1", true);

        when(keyLookup.findByApiKey("approved-test-key"))
                .thenReturn(Optional.of(merchant));

        MerchantKeyDetails result =
                authenticator.authenticate("approved-test-key");

        assertEquals(merchant, result);
        verify(keyLookup).findByApiKey("approved-test-key");
    }
}