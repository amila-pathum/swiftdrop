package com.swiftdrop.identity_service.config;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(repository, encoder, email, password, "SwiftDrop Administrator");
    }

    @Test
    void doesNothingWhenNotConfigured() {
        bootstrap("", "").run(null);
        bootstrap(null, null).run(null);

        verifyNoInteractions(repository);
    }

    @Test
    void createsAnActiveAdminWithAHashedPassword() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        bootstrap(" Admin@Example.com ", "a-long-password").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("a-long-password");
        assertThat(encoder.matches("a-long-password", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void failsStartupWhenEmailBelongsToNonAdminAccount() {
        User customer = new User("Someone", "admin@example.com", "existing-hash",
            Role.CUSTOMER, UserStatus.ACTIVE);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> bootstrap("admin@example.com", "a-long-password").run(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("non-admin account");
        verify(repository, never()).save(any());
        assertThat(customer.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void skipsWhenEmailBelongsToExistingAdmin() {
        User existingAdmin = new User("Admin", "admin@example.com", "existing-hash",
            Role.ADMIN, UserStatus.ACTIVE);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(existingAdmin));

        bootstrap("admin@example.com", "a-long-password").run(null);

        verify(repository, never()).save(any());
        assertThat(existingAdmin.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void neverChangesAnExistingAdministrator() {
        User admin = new User("Existing Admin", "admin@example.com", "existing-hash",
            Role.ADMIN, UserStatus.ACTIVE);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        bootstrap("admin@example.com", "a-different-password").run(null);

        verify(repository, never()).save(any());
        assertThat(admin.getPasswordHash()).isEqualTo("existing-hash");
    }

    @Test
    void doesNotCreateAnotherAdministratorUnderADifferentEmail() {
        when(repository.findByEmail("new-admin@example.com")).thenReturn(Optional.empty());
        when(repository.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap("new-admin@example.com", "a-long-password").run(null);

        verify(repository, never()).save(any());
    }

    @Test
    void failsStartupWhenOnlyOneValueIsSet() {
        assertThatThrownBy(() -> bootstrap("admin@example.com", "").run(null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("set together");
        assertThatThrownBy(() -> bootstrap("", "a-long-password").run(null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("set together");
        verifyNoInteractions(repository);
    }

    @Test
    void failsStartupForAWeakPasswordOrInvalidEmail() {
        assertThatThrownBy(() -> bootstrap("admin@example.com", "short").run(null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("12 to 72");
        assertThatThrownBy(() -> bootstrap("not-an-email", "a-long-password").run(null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("email address");
        verifyNoInteractions(repository);
    }
}
