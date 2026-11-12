package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.ChangePasswordRequest;
import com.swiftdrop.identity_service.dto.UpdateProfileRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.InvalidCurrentPasswordException;
import com.swiftdrop.identity_service.exception.PasswordMismatchException;
import com.swiftdrop.identity_service.exception.SamePasswordException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(long userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(long userId, UpdateProfileRequest request) {
        var user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
        user.updateProfile(
            request.name().trim(),
            request.phone() != null ? request.phone().trim() : null,
            request.address() != null ? request.address().trim() : null
        );
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest request) {
        String newPassword = request.newPassword();
        String confirmPassword = request.confirmPassword();

        if (confirmPassword == null || !confirmPassword.equals(newPassword)) {
            throw new PasswordMismatchException("New password and confirm password do not match");
        }

        if (request.currentPassword().equals(newPassword)) {
            throw new SamePasswordException("New password cannot be the same as current password");
        }

        var user = userRepository.findById(userId)
            .orElseThrow(UserNotFoundException::new);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        if (!user.hasPassword()
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new SamePasswordException("New password cannot be the same as current password");
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
