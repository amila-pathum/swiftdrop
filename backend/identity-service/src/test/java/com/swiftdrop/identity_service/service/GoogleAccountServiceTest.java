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
import com.swiftdrop.identity_service.service.GoogleAccountService.GoogleProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GoogleAccountServiceTest {

    private static final String SUB = "google-sub-123";

    private final UserRepository users = mock(UserRepository.class);
    private final UserIdentityRepository identities = mock(UserIdentityRepository.class);
    private GoogleAccountService service;

    @BeforeEach
    void setUp() {
        service = new GoogleAccountService(users, identities);
        when(identities.findByProviderAndProviderSubject(any(), any())).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static GoogleProfile profile(String email, boolean verified, String name) {
        return new GoogleProfile(SUB, email, verified, name);
    }

    private static Reason reasonOf(Runnable action) {
        try {
            action.run();
        } catch (GoogleSignInException exception) {
            return exception.getReason();
        }
        throw new AssertionError("Expected GoogleSignInException");
    }

    @Test
    void createsAnActiveCustomerWithoutPasswordAndLinksTheGoogleAccount() {
        User user = service.resolveUser(profile(" New.User@Gmail.com ", true, " New User "));

        assertThat(user.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getEmail()).isEqualTo("new.user@gmail.com");
        assertThat(user.getName()).isEqualTo("New User");
        assertThat(user.hasPassword()).isFalse();

        ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identities).save(identity.capture());
        assertThat(identity.getValue().getUser()).isSameAs(user);
        assertThat(identity.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(identity.getValue().getProviderSubject()).isEqualTo(SUB);
    }

    @Test
    void signsInTheLinkedUserWithoutChangingIt() {
        User merchant = new User("Linked Merchant", "merchant@example.com", "hash",
            Role.MERCHANT, UserStatus.ACTIVE);
        when(identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUB))
            .thenReturn(Optional.of(new UserIdentity(merchant, AuthProvider.GOOGLE, SUB, "x@gmail.com")));

        User user = service.resolveUser(profile("different@gmail.com", true, "Other Name"));

        assertThat(user).isSameAs(merchant);
        assertThat(user.getName()).isEqualTo("Linked Merchant");
        assertThat(user.getEmail()).isEqualTo("merchant@example.com");
        assertThat(user.getRole()).isEqualTo(Role.MERCHANT);
        verify(users, never()).save(any());
        verify(identities, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void refusesALinkedUserWhoIsNotActive(UserStatus status) {
        User inactive = new User("Inactive", "inactive@example.com", null, Role.CUSTOMER, status);
        when(identities.findByProviderAndProviderSubject(AuthProvider.GOOGLE, SUB))
            .thenReturn(Optional.of(new UserIdentity(inactive, AuthProvider.GOOGLE, SUB, "x@gmail.com")));

        assertThat(reasonOf(() -> service.resolveUser(profile("x@gmail.com", true, "X"))))
            .isEqualTo(Reason.ACCOUNT_INACTIVE);
    }

    @Test
    void refusesAnUnverifiedEmailWithoutCreatingAnything() {
        assertThat(reasonOf(() -> service.resolveUser(profile("new@gmail.com", false, "New"))))
            .isEqualTo(Reason.EMAIL_UNVERIFIED);
        verify(users, never()).save(any());
        verify(identities, never()).save(any());
    }

    @Test
    void refusesToLinkAnExistingAccountWithTheSameEmail() {
        when(users.existsByEmail("existing@example.com")).thenReturn(true);

        assertThat(reasonOf(() -> service.resolveUser(profile("Existing@Example.com", true, "X"))))
            .isEqualTo(Reason.ACCOUNT_EXISTS);
        verify(users, never()).save(any());
        verify(identities, never()).save(any());
    }

    @Test
    void refusesAProfileWithoutSubjectOrEmail() {
        assertThat(reasonOf(() -> service.resolveUser(new GoogleProfile(" ", "a@gmail.com", true, "A"))))
            .isEqualTo(Reason.INVALID_PROFILE);
        assertThat(reasonOf(() -> service.resolveUser(profile(null, true, "A"))))
            .isEqualTo(Reason.INVALID_PROFILE);
        assertThat(reasonOf(() -> service.resolveUser(profile("not-an-email", true, "A"))))
            .isEqualTo(Reason.INVALID_PROFILE);
        verify(users, never()).save(any());
    }

    @Test
    void derivesANameWhenGoogleProvidesNoneAndTruncatesLongNames() {
        assertThat(service.resolveUser(profile("jane.doe@gmail.com", true, "  ")).getName())
            .isEqualTo("jane.doe");
        assertThat(service.resolveUser(profile("long@gmail.com", true, "n".repeat(150))).getName())
            .hasSize(100);
    }

    @Test
    void neverCreatesAPrivilegedAccount() {
        service.resolveUser(profile("someone@gmail.com", true, "Someone"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void exceptionsCarryOnlyAShortReasonCode() {
        assertThatThrownBy(() -> service.resolveUser(profile("new@gmail.com", false, "New")))
            .hasMessage("Google sign-in refused: email_unverified");
    }
}
