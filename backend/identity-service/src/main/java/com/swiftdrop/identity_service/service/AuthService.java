package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.GoogleExchangeRequest;
import com.swiftdrop.identity_service.dto.LoginRequest;
import com.swiftdrop.identity_service.dto.LoginResponse;
import com.swiftdrop.identity_service.dto.RegisterRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.DuplicateEmailException;
import com.swiftdrop.identity_service.exception.InvalidCredentialsException;
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

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String name = request.name().trim();

        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        User user = new User(
            name,
            email,
            passwordEncoder.encode(request.password()),
            Role.CUSTOMER,
            UserStatus.ACTIVE
        );

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

    private LoginResponse issueToken(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(),
            UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
