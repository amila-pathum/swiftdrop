package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.CreateStaffRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.DuplicateEmailException;
import com.swiftdrop.identity_service.exception.InvalidStatusChangeException;
import com.swiftdrop.identity_service.exception.RoleNotAllowedException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Account operations reserved for administrators: creating staff accounts and
 * activating, suspending or disabling accounts. Callers are authorized by the
 * controller; this class holds the rules.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Creates an ACTIVE DISPATCHER or ADMIN. Other roles register themselves. */
    @Transactional
    public UserResponse createStaff(CreateStaffRequest request) {
        if (!request.role().isStaff()) {
            throw new RoleNotAllowedException();
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        User user = new User(request.name().trim(), email,
            passwordEncoder.encode(request.password()), request.role(), UserStatus.ACTIVE);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(long userId) {
        return UserResponse.from(find(userId));
    }

    /**
     * Activates, suspends or disables an account. This is how a merchant or
     * courier becomes ACTIVE once the owning service has approved the
     * application. Tokens issued under the old status stop working at once.
     */
    @Transactional
    public UserResponse changeStatus(long adminId, long userId, UserStatus status) {
        if (adminId == userId) {
            // An administrator locking themselves out could leave no admin at all.
            throw new InvalidStatusChangeException();
        }
        User user = find(userId);
        if (status == UserStatus.PENDING_APPROVAL
                && user.getRole().initialStatus() != UserStatus.PENDING_APPROVAL) {
            throw new InvalidStatusChangeException();
        }
        if (user.getStatus() != status) {
            user.changeStatus(status);
            user.invalidateTokens();
            userRepository.save(user);
        }
        return UserResponse.from(user);
    }

    private User find(long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
