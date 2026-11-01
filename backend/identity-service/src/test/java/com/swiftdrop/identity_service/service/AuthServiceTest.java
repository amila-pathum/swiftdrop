package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.GoogleExchangeRequest;
import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.RegisterRequest;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.DuplicateEmailException;
import com.swiftdrop.identity_service.exception.InvalidCredentialsException;
import com.swiftdrop.identity_service.exception.InvalidLoginCodeException;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private static final String VERIFIER = "v".repeat(43);

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final OAuthLoginCodeService loginCodes = mock(OAuthLoginCodeService.class);
    private AuthService authService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        authService = new AuthService(repository, encoder, jwtService, loginCodes);
        activeUser = user(UserStatus.ACTIVE);
    }

    @Test
    void registerAlwaysCreatesActiveCustomerWithEncodedPassword() {
        when(repository.existsByEmail("new@example.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("encoded");
        when(repository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        var response = authService.register(
            new RegisterRequest(" New User ", " NEW@example.com ", "password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("encoded");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(repository.existsByEmail("user@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
            new RegisterRequest("Someone", "User@Example.com", "password123")))
            .isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void loginReturnsTokenAndPublicUserForValidCredentials() {
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(encoder.matches("password123", "hash")).thenReturn(true);
        when(jwtService.generateToken(activeUser)).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);

        var response = authService.login(new LoginRequest(" USER@example.com ", "password123"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().email()).isEqualTo("user@example.com");
        assertThat(response.user().role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void loginRejectsWrongPasswordWithoutIssuingToken() {
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(activeUser));
        when(encoder.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
            new LoginRequest("user@example.com", "wrong-password")))
            .isInstanceOf(InvalidCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(repository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
            new LoginRequest("missing@example.com", "password123")))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsAccountWithoutPasswordLikeAWrongPassword() {
        User googleOnly = new User("Google User", "user@example.com", null,
            Role.CUSTOMER, UserStatus.ACTIVE);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(googleOnly));

        assertThatThrownBy(() -> authService.login(
            new LoginRequest("user@example.com", "any-password")))
            .isInstanceOf(InvalidCredentialsException.class);
        verify(encoder, never()).matches(anyString(), any());
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void loginRejectsNonActiveAccount() {
        User suspended = user(UserStatus.SUSPENDED);
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(suspended));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(
            new LoginRequest("user@example.com", "password123")))
            .isInstanceOf(AccountNotActiveException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void googleExchangeReturnsTheSameResponseAsPasswordLogin() {
        when(loginCodes.redeem("one-time-code", VERIFIER)).thenReturn(activeUser);
        when(jwtService.generateToken(activeUser)).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);

        var response = authService.exchangeGoogleCode(
            new GoogleExchangeRequest("one-time-code", VERIFIER));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void googleExchangeRejectsAnInvalidCode() {
        when(loginCodes.redeem(anyString(), anyString())).thenThrow(new InvalidLoginCodeException());

        assertThatThrownBy(() -> authService.exchangeGoogleCode(
            new GoogleExchangeRequest("bad-code", VERIFIER)))
            .isInstanceOf(InvalidLoginCodeException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void googleExchangeRejectsAnAccountSuspendedAfterSignIn() {
        when(loginCodes.redeem("one-time-code", VERIFIER)).thenReturn(user(UserStatus.SUSPENDED));

        assertThatThrownBy(() -> authService.exchangeGoogleCode(
            new GoogleExchangeRequest("one-time-code", VERIFIER)))
            .isInstanceOf(AccountNotActiveException.class);
        verify(jwtService, never()).generateToken(any());
    }

    private User user(UserStatus status) {
        User user = new User("Test User", "user@example.com", "hash", Role.CUSTOMER, status);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.invokeMethod(user, "onCreate");
        return user;
    }
}
