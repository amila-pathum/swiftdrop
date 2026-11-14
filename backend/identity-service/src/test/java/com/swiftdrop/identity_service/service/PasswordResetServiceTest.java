package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.entity.PasswordResetToken;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.InvalidResetTokenException;
import com.swiftdrop.identity_service.exception.InvalidStatusChangeException;
import com.swiftdrop.identity_service.exception.PasswordResetUnavailableException;
import com.swiftdrop.identity_service.repository.PasswordResetTokenRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Rules of password reset; storage behaviour is covered by AccountLifecycleApiTest. */
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PasswordResetDelivery delivery = mock(PasswordResetDelivery.class);
    private final AuditLogService auditLog = mock(AuditLogService.class);

    private PasswordResetService service(PasswordResetDelivery configuredDelivery) {
        return new PasswordResetService(tokens, users, encoder, configuredDelivery,
            auditLog, "https://swiftdrop.example/", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenIsRandomStoredOnlyAsAHashAndExpiresAfterThirtyMinutes() {
        User user = user(UserStatus.ACTIVE);
        when(users.findById(1L)).thenReturn(Optional.of(user));

        var first = service(null).issueFor(99L, 1L);
        var second = service(null).issueFor(99L, 1L);

        assertThat(first.token()).hasSize(43).isNotEqualTo(second.token());
        assertThat(first.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(first.resetUrl())
            .isEqualTo("https://swiftdrop.example/reset-password#token=" + first.token());

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getTokenHash())
            .hasSize(64).isNotEqualTo(first.token()).doesNotContain(first.token());
        verify(tokens, org.mockito.Mockito.times(2)).deleteByUserId(1L);
    }

    @Test
    void tokensAreNotIssuedForAccountsThatCannotSignIn() {
        when(users.findById(1L)).thenReturn(Optional.of(user(UserStatus.DISABLED)));

        assertThatThrownBy(() -> service(null).issueFor(99L, 1L))
            .isInstanceOf(AccountNotActiveException.class);
        verify(tokens, never()).save(any());
    }

    @Test
    void tokensAreNotIssuedForAdminAccounts() {
        User admin = new User("Admin", "admin@example.com", "hash", Role.ADMIN, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(admin, "id", 2L);
        when(users.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service(null).issueFor(99L, 2L))
            .isInstanceOf(InvalidStatusChangeException.class)
            .hasMessageContaining("admin");
        verify(tokens, never()).save(any());
    }

    @Test
    void emailRequestIsRefusedWithoutADeliveryIntegration() {
        assertThatThrownBy(() -> service(null).requestByEmail("user@example.com"))
            .isInstanceOf(PasswordResetUnavailableException.class);
        verifyNoInteractions(users, tokens);
    }

    @Test
    void emailRequestDeliversALinkToARegisteredUser() {
        User user = user(UserStatus.ACTIVE);
        when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        service(delivery).requestByEmail("  User@Example.com ");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(delivery).send(eq(user), url.capture(), eq(NOW.plusSeconds(1800)));
        assertThat(url.getValue()).startsWith("https://swiftdrop.example/reset-password#token=");
    }

    @Test
    void emailRequestLooksTheSameForUnknownAndBlockedAccounts() {
        when(users.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("blocked@example.com"))
            .thenReturn(Optional.of(user(UserStatus.SUSPENDED)));

        service(delivery).requestByEmail("nobody@example.com");
        service(delivery).requestByEmail("blocked@example.com");

        verifyNoInteractions(delivery);
        verify(tokens, never()).save(any());
    }

    @Test
    void confirmSetsTheHashedPasswordAndInvalidatesExistingTokens() {
        User user = user(UserStatus.ACTIVE);
        when(tokens.markUsed(anyString(), eq(NOW))).thenReturn(1);
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(
            new PasswordResetToken("hash", user, NOW, NOW.plusSeconds(1800))));
        when(encoder.encode("new-password-456")).thenReturn("new-hash");

        service(null).confirm("raw-token", "new-password-456");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(users).save(user);
    }

    @Test
    void confirmRefusesUnknownUsedOrExpiredTokensAlike() {
        when(tokens.markUsed(anyString(), eq(NOW))).thenReturn(0);

        assertThatThrownBy(() -> service(null).confirm("raw-token", "new-password-456"))
            .isInstanceOf(InvalidResetTokenException.class);
        assertThatThrownBy(() -> service(null).confirm(" ", "new-password-456"))
            .isInstanceOf(InvalidResetTokenException.class);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void confirmRefusesAnAccountSuspendedAfterTheTokenWasIssued() {
        User suspended = user(UserStatus.SUSPENDED);
        when(tokens.markUsed(anyString(), eq(NOW))).thenReturn(1);
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(
            new PasswordResetToken("hash", suspended, NOW, NOW.plusSeconds(1800))));

        assertThatThrownBy(() -> service(null).confirm("raw-token", "new-password-456"))
            .isInstanceOf(InvalidResetTokenException.class);
        verify(users, never()).save(any());
    }

    private static User user(UserStatus status) {
        User user = new User("Test User", "user@example.com", "old-hash", Role.CUSTOMER, status);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
