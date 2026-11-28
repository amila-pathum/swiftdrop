package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.GoogleExchangeRequest;
import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.LoginResponse;
import com.swiftdrop.identity_service.dto.RefreshRequest;
import com.swiftdrop.identity_service.dto.RegisterRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.DuplicateEmailException;
import com.swiftdrop.identity_service.exception.InvalidCredentialsException;
import com.swiftdrop.identity_service.exception.RateLimitExceededException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.security.JwtService;
import com.swiftdrop.identity_service.security.RateLimiter;

import com.swiftdrop.identity_service.notification.NotificationEmailClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    static final String DUMMY_HASH =
        "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OAuthLoginCodeService loginCodes;
    private final RateLimiter rateLimiter;
    private final RefreshTokenService refreshTokens;
    private NotificationEmailClient emails;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OAuthLoginCodeService loginCodes,
            RateLimiter rateLimiter,
            RefreshTokenService refreshTokens) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginCodes = loginCodes;
        this.rateLimiter = rateLimiter;
        this.refreshTokens = refreshTokens;
    }

    /** Optional: present only when email delivery is configured. */
    @Autowired(required = false)
    void setEmails(NotificationEmailClient emails) {
        this.emails = emails;
    }

    /** Customer sign-up: an ACTIVE CUSTOMER that can be used straight away. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        return createAccount(request, Role.CUSTOMER);
    }

    /**
     * Merchant sign-up: creates only the identity account (MERCHANT, awaiting
     * approval). The business application itself belongs to the Merchant Service.
     */
    @Transactional
    public UserResponse registerMerchant(RegisterRequest request) {
        return createAccount(request, Role.MERCHANT);
    }

    /**
     * Courier sign-up: creates only the identity account (COURIER, awaiting
     * approval). The courier application itself belongs to the Courier Service.
     */
    @Transactional
    public UserResponse registerCourier(RegisterRequest request) {
        return createAccount(request, Role.COURIER);
    }

    private UserResponse createAccount(RegisterRequest request, Role role) {
        String email = normalizeEmail(request.email());
        if (!rateLimiter.isAllowed("register:" + email)) {
            throw new RateLimitExceededException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User(
            request.name().trim(),
            email,
            passwordEncoder.encode(request.password()),
            role,
            role.initialStatus()
        );
        user.updateProfile(user.getName(), blankToNull(request.phone()),
            blankToNull(request.address()));

        User saved = userRepository.save(user);
        if (emails != null) {
            emails.sendWelcome(saved);
        }
        return UserResponse.from(saved);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        if (!rateLimiter.isAllowed("login:" + email)) {
            throw new RateLimitExceededException();
        }

        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new InvalidCredentialsException();
        }
        User user = found.get();

        if (!user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueToken(user, request.persistent());
    }

    /**
     * Exchanges a one-time Google sign-in code for the same response as a password
     * login. Deliberately not transactional: a failed attempt must still consume
     * the code, which the redeem transaction commits on its own.
     */
    public LoginResponse exchangeGoogleCode(GoogleExchangeRequest request) {
        return issueToken(loginCodes.redeem(request.code(), request.verifier()), false);
    }

    /**
     * Exchanges a refresh token for a new access token and a new refresh token.
     * The old refresh token cannot be used again.
     */
    public LoginResponse refresh(RefreshRequest request) {
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(request.refreshToken());
        return response(rotation.user(), rotation.refreshToken());
    }

    /**
     * Signs the user out everywhere: every access and refresh token issued so
     * far stops working at the Identity Service at once. Other services verify
     * access tokens without a database lookup, so there one ends only when it
     * expires.
     */
    @Transactional
    public void logout(long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.invalidateTokens();
        userRepository.save(user);
        refreshTokens.revokeAll(userId);
    }

    // ACTIVE and PENDING_APPROVAL accounts may sign in; a pending merchant or
    // courier gets a token without role authority (see JwtAuthenticationFilter).
    // Suspended and disabled accounts are refused.
    private LoginResponse issueToken(User user, boolean persistent) {
        if (!user.getStatus().canSignIn()) {
            throw new AccountNotActiveException();
        }
        return response(user, refreshTokens.issue(user, persistent));
    }

    private LoginResponse response(User user, RefreshTokenService.IssuedRefreshToken refresh) {
        return new LoginResponse(jwtService.generateToken(user), "Bearer",
            jwtService.getExpirationSeconds(), refresh.token(), refresh.expiresInSeconds(),
            UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
