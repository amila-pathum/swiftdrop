package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.entity.AuthProvider;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserIdentity;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.GoogleSignInException;
import com.swiftdrop.identity_service.exception.GoogleSignInException.Reason;
import com.swiftdrop.identity_service.repository.UserIdentityRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Decides which SwiftDrop user a verified Google sign-in belongs to.
 *
 * <ul>
 *   <li>A Google account already linked (by "sub") signs in as its user, if ACTIVE.</li>
 *   <li>Otherwise the Google email must be verified.</li>
 *   <li>If no SwiftDrop account uses the email, an ACTIVE CUSTOMER without a
 *       password is created and linked.</li>
 *   <li>If one does, sign-in is refused. Accounts are never linked automatically
 *       by email: SwiftDrop does not verify registration emails, so the existing
 *       account may not belong to the Google user.</li>
 * </ul>
 * Existing users' names, emails, passwords and roles are never changed here.
 */
@Service
public class GoogleAccountService {

    private static final int NAME_MAX = 100;
    private static final int EMAIL_MAX = 255;

    /** Claims taken from Google's validated ID token. */
    public record GoogleProfile(String subject, String email, boolean emailVerified, String name) {}

    private final UserRepository userRepository;
    private final UserIdentityRepository identityRepository;

    public GoogleAccountService(UserRepository userRepository,
            UserIdentityRepository identityRepository) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public User resolveUser(GoogleProfile profile) {
        if (profile.subject() == null || profile.subject().isBlank()) {
            throw new GoogleSignInException(Reason.INVALID_PROFILE);
        }

        var linked = identityRepository.findByProviderAndProviderSubject(
            AuthProvider.GOOGLE, profile.subject());
        if (linked.isPresent()) {
            return requireActive(linked.get().getUser());
        }

        if (!profile.emailVerified()) {
            throw new GoogleSignInException(Reason.EMAIL_UNVERIFIED);
        }
        String email = normalizeEmail(profile.email());
        if (userRepository.existsByEmail(email)) {
            throw new GoogleSignInException(Reason.ACCOUNT_EXISTS);
        }

        User user = userRepository.save(new User(
            displayName(profile.name(), email), email, null, Role.CUSTOMER, UserStatus.ACTIVE));
        identityRepository.save(
            new UserIdentity(user, AuthProvider.GOOGLE, profile.subject(), email));
        return user;
    }

    private static User requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new GoogleSignInException(Reason.ACCOUNT_INACTIVE);
        }
        return user;
    }

    private static String normalizeEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > EMAIL_MAX || !normalized.contains("@")) {
            throw new GoogleSignInException(Reason.INVALID_PROFILE);
        }
        return normalized;
    }

    private static String displayName(String name, String email) {
        String value = name == null || name.isBlank()
            ? email.substring(0, email.indexOf('@'))
            : name.trim();
        return value.length() > NAME_MAX ? value.substring(0, NAME_MAX) : value;
    }
}
