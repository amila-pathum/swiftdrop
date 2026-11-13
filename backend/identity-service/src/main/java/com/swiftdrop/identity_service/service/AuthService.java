package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.GoogleExchangeRequest;
import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.LoginResponse;
import com.swiftdrop.identity_service.dto.RegisterRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.DuplicateEmailException;
import com.swiftdrop.identity_service.exception.InvalidCredentialsException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.security.JwtService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OAuthLoginCodeService loginCodes;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OAuthLoginCodeService loginCodes) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginCodes = loginCodes;
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

    // The role is chosen by the endpoint, never read from the request.
    private UserResponse createAccount(RegisterRequest request, Role role) {
        String email = normalizeEmail(request.email());
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

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
            .orElseThrow(InvalidCredentialsException::new);

        // Google-only accounts have no password; they fail like a wrong password.
        if (!user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return issueToken(user);
    }

    /**
     * Exchanges a one-time Google sign-in code for the same response as a password
     * login. Deliberately not transactional: a failed attempt must still consume
     * the code, which the redeem transaction commits on its own.
     */
    public LoginResponse exchangeGoogleCode(GoogleExchangeRequest request) {
        return issueToken(loginCodes.redeem(request.code(), request.verifier()));
    }

    /**
     * Signs the user out everywhere: every access token issued so far stops
     * working at the Identity Service at once. Other services verify tokens
     * without a database lookup, so there a token ends only when it expires.
     */
    @Transactional
    public void logout(long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.invalidateTokens();
        userRepository.save(user);
    }

    // ACTIVE and PENDING_APPROVAL accounts may sign in; a pending merchant or
    // courier gets a token without role authority (see JwtAuthenticationFilter).
    // Suspended and disabled accounts are refused.
    private LoginResponse issueToken(User user) {
        if (!user.getStatus().canSignIn()) {
            throw new AccountNotActiveException();
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(),
            UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
