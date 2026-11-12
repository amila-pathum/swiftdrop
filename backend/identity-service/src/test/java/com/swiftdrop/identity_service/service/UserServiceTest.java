package com.swiftdrop.identity_service.service;

import com.swiftdrop.identity_service.dto.UpdateProfileRequest;
import com.swiftdrop.identity_service.dto.UserResponse;
import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.exception.AccountNotActiveException;
import com.swiftdrop.identity_service.exception.UserNotFoundException;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private UserService userService;
    private User activeUser;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
        activeUser = createUser(1L, "John Doe", "john@example.com", UserStatus.ACTIVE);
    }

    @Test
    void getCurrentUserReturnsProfileForActiveUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));

        UserResponse response = userService.getCurrentUser(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("John Doe");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.phone()).isNull();
        assertThat(response.address()).isNull();
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.createdAt()).isNotNull();
    }

    @Test
    void getCurrentUserThrowsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(99L))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getCurrentUserThrowsWhenAccountNotActive() {
        User suspendedUser = createUser(2L, "Suspended", "suspended@example.com", UserStatus.SUSPENDED);
        when(userRepository.findById(2L)).thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(() -> userService.getCurrentUser(2L))
            .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void updateProfileUpdatesFieldsAndSavesUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest(
            " Jane Doe ",
            "+1-555-1234",
            "456 Elm St, City"
        );

        UserResponse response = userService.updateProfile(1L, request);

        ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedCaptor.capture());
        User savedUser = savedCaptor.getValue();

        assertThat(savedUser.getName()).isEqualTo("Jane Doe");
        assertThat(savedUser.getPhone()).isEqualTo("+1-555-1234");
        assertThat(savedUser.getAddress()).isEqualTo("456 Elm St, City");
        assertThat(savedUser.getEmail()).isEqualTo("john@example.com");
        assertThat(savedUser.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        assertThat(response.name()).isEqualTo("Jane Doe");
        assertThat(response.phone()).isEqualTo("+1-555-1234");
        assertThat(response.address()).isEqualTo("456 Elm St, City");
        assertThat(response.email()).isEqualTo("john@example.com");
    }

    @Test
    void updateProfileAllowsNullPhoneAndAddress() {
        activeUser.updateProfile("John Doe", "+123456", "Some Address");
        when(userRepository.findById(1L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("New Name", null, null);
        UserResponse response = userService.updateProfile(1L, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.phone()).isNull();
        assertThat(response.address()).isNull();
    }

    @Test
    void updateProfileThrowsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest("Name", "123", "Addr");
        assertThatThrownBy(() -> userService.updateProfile(99L, request))
            .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfileThrowsWhenAccountNotActive() {
        User pendingUser = createUser(3L, "Pending", "pending@example.com", UserStatus.PENDING_APPROVAL);
        when(userRepository.findById(3L)).thenReturn(Optional.of(pendingUser));

        UpdateProfileRequest request = new UpdateProfileRequest("Name", "123", "Addr");
        assertThatThrownBy(() -> userService.updateProfile(3L, request))
            .isInstanceOf(AccountNotActiveException.class);
        verify(userRepository, never()).save(any());
    }

    private User createUser(long id, String name, String email, UserStatus status) {
        User user = new User(name, email, "hashed-pw", Role.CUSTOMER, status);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.invokeMethod(user, "onCreate");
        return user;
    }
}
