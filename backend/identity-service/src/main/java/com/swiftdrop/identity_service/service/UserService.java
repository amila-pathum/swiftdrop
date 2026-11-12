package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.UpdateProfileRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
}
