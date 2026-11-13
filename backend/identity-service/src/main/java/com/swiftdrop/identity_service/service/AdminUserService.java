package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.CreateStaffRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.Role;
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

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLog;

    public AdminUserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            AuditLogService auditLog) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLog = auditLog;
    }

    @Transactional
    public UserResponse createStaff(long adminId, CreateStaffRequest request) {
        if (!request.role().isStaff()) {
            throw new RoleNotAllowedException();
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        User user = new User(request.name().trim(), email,
            passwordEncoder.encode(request.password()), request.role(), UserStatus.ACTIVE);
        user = userRepository.save(user);
        auditLog.logAdminAction(adminId, "CREATE_STAFF", user.getId(),
            "role=" + request.role());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(long userId) {
        return UserResponse.from(find(userId));
    }

    @Transactional
    public UserResponse changeStatus(long adminId, long userId, UserStatus status) {
        if (adminId == userId) {
            throw new InvalidStatusChangeException();
        }
        User user = find(userId);
        if (status == UserStatus.PENDING_APPROVAL
                && user.getRole().initialStatus() != UserStatus.PENDING_APPROVAL) {
            throw new InvalidStatusChangeException();
        }
        UserStatus oldStatus = user.getStatus();
        if (oldStatus != status) {
            user.changeStatus(status);
            user.invalidateTokens();
            userRepository.save(user);
            auditLog.logAdminAction(adminId, "CHANGE_STATUS", userId,
                oldStatus + " -> " + status);
        }
        return UserResponse.from(user);
    }

    /**
     * Activates a PENDING_APPROVAL merchant or courier. The Merchant or Courier
     * Service calls this after approving the business application, forwarding
     * the approving administrator's own Authorization header.
     */
    @Transactional
    public UserResponse activate(long adminId, long userId) {
        User user = find(userId);
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new InvalidStatusChangeException("Account is not pending approval");
        }
        if (user.getRole() != Role.MERCHANT && user.getRole() != Role.COURIER) {
            throw new InvalidStatusChangeException("Only merchant and courier accounts can be activated");
        }
        user.changeStatus(UserStatus.ACTIVE);
        user.invalidateTokens();
        userRepository.save(user);
        auditLog.logAdminAction(adminId, "ACTIVATE", userId,
            "role=" + user.getRole());
        return UserResponse.from(user);
    }

    /**
     * Records that the administrator has seen and rejected the application.
     * The identity account stays PENDING_APPROVAL so the user can still sign in
     * and view the rejection; the business application status is owned by the
     * Merchant or Courier Service.
     */
    @Transactional
    public UserResponse reject(long adminId, long userId) {
        User user = find(userId);
        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new InvalidStatusChangeException("Account is not pending approval");
        }
        if (user.getRole() != Role.MERCHANT && user.getRole() != Role.COURIER) {
            throw new InvalidStatusChangeException("Only merchant and courier accounts can be rejected");
        }
        auditLog.logAdminAction(adminId, "REJECT", userId,
            "role=" + user.getRole());
        return UserResponse.from(user);
    }

    private User find(long userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
