package com.swiftdrop.identity_service;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.OAuthLoginCodeRepository;
import com.swiftdrop.identity_service.repository.PasswordResetTokenRepository;
import com.swiftdrop.identity_service.repository.UserIdentityRepository;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.identity_service.security.AuthenticatedUser;
import com.swiftdrop.identity_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "jwt.expiration-ms=900000",
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class SecurityApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean UserIdentityRepository userIdentityRepository;
    @MockitoBean OAuthLoginCodeRepository oauthLoginCodeRepository;
    @MockitoBean PasswordResetTokenRepository passwordResetTokenRepository;

    private User user;

    /** Test-only endpoints used to prove role restrictions; not part of the production API. */
    @TestConfiguration
    static class RoleRestrictedTestEndpoints {

        @Bean
        RoleRestrictedController roleRestrictedController() {
            return new RoleRestrictedController();
        }
    }

    @RestController
    static class RoleRestrictedController {

        @GetMapping("/test/rbac/customer")
        @PreAuthorize("hasRole('CUSTOMER')")
        Map<String, String> customerOnly() {
            return Map.of("access", "customer");
        }

        @GetMapping("/test/rbac/merchant")
        @PreAuthorize("hasRole('MERCHANT')")
        Map<String, String> merchantOnly() {
            return Map.of("access", "merchant");
        }
    }

    @BeforeEach
    void setUp() {
        user = user(1L, "user@example.com", Role.CUSTOMER, UserStatus.ACTIVE);
    }

    @Test
    void registrationIsPublic() throws Exception {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"New User","email":"new@example.com","password":"password123"}
                    """))
            .andExpect(status().isCreated());
    }

    @Test
    void registrationIgnoresClientSuppliedRole() throws Exception {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"New User","email":"new@example.com","password":"password123",
                     "role":"MERCHANT","authorities":["ROLE_DISPATCHER"]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registrationIgnoresClientSuppliedStatus() throws Exception {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"New User","email":"new@example.com","password":"password123",
                     "status":"DISABLED"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void registrationRejectsDuplicateEmail() throws Exception {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Someone","email":"User@Example.com","password":"password123"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Email is already registered"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registrationRejectsInvalidData() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","email":"not-an-email","password":"short"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginIsPublic() throws Exception {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"user@example.com","password":"password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        AuthenticatedUser claims = jwtService.parseToken(JsonPath.read(body, "$.accessToken"));
        assertThat(claims).isEqualTo(new AuthenticatedUser(1L, "user@example.com", Role.CUSTOMER));
    }

    @Test
    void customerCanAccessCustomerResource() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/test/rbac/customer")
                .header("Authorization", "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access").value("customer"));
    }

    @Test
    void customerIsDeniedMerchantResource() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/test/rbac/merchant")
                .header("Authorization", "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Access is denied"));
    }

    @Test
    void merchantCanAccessMerchantResource() throws Exception {
        User merchant = user(2L, "merchant@example.com", Role.MERCHANT, UserStatus.ACTIVE);
        when(userRepository.findById(2L)).thenReturn(Optional.of(merchant));

        mockMvc.perform(get("/test/rbac/merchant")
                .header("Authorization", "Bearer " + jwtService.generateToken(merchant)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access").value("merchant"));
    }

    @Test
    void roleRestrictedResourceRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/test/rbac/merchant"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Authentication is required"));
    }

    @Test
    void tokenIsRejectedWhenDatabaseRoleNoLongerMatches() throws Exception {
        String customerToken = jwtService.generateToken(user);
        User promoted = user(1L, "user@example.com", Role.MERCHANT, UserStatus.ACTIVE);
        when(userRepository.findById(1L)).thenReturn(Optional.of(promoted));

        mockMvc.perform(get("/test/rbac/merchant")
                .header("Authorization", "Bearer " + customerToken))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
    }

    @Test
    void tokenIsRejectedForInactiveAccount() throws Exception {
        String token = jwtService.generateToken(user);
        User suspended = user(1L, "user@example.com", Role.CUSTOMER, UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(suspended));

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
    }

    @Test
    void loginRejectsInactiveAccount() throws Exception {
        User suspended = user(3L, "suspended@example.com", Role.CUSTOMER,
            UserStatus.SUSPENDED);
        when(userRepository.findByEmail("suspended@example.com"))
            .thenReturn(Optional.of(suspended));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"suspended@example.com","password":"password123"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("This account is not permitted to sign in"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void pendingMerchantSignsInButIsDeniedMerchantResources() throws Exception {
        User pending = user(4L, "pending@example.com", Role.MERCHANT,
            UserStatus.PENDING_APPROVAL);
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(userRepository.findById(4L)).thenReturn(Optional.of(pending));

        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"pending@example.com","password":"password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.status").value("PENDING_APPROVAL"))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("MERCHANT"));
        mockMvc.perform(get("/test/rbac/merchant").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Access is denied"));
    }

    @Test
    void tokenIsRejectedOnceTheAccountStatusChanges() throws Exception {
        User pending = user(4L, "pending@example.com", Role.MERCHANT,
            UserStatus.PENDING_APPROVAL);
        String pendingToken = jwtService.generateToken(pending);
        User activated = user(4L, "pending@example.com", Role.MERCHANT, UserStatus.ACTIVE);
        when(userRepository.findById(4L)).thenReturn(Optional.of(activated));

        mockMvc.perform(get("/test/rbac/merchant")
                .header("Authorization", "Bearer " + pendingToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenIsRejectedAfterTheTokenVersionChanges() throws Exception {
        String token = jwtService.generateToken(user);
        user.invalidateTokens();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
    }

    @Test
    void adminEndpointsRejectEveryOtherRole() throws Exception {
        for (Role role : new Role[] {Role.CUSTOMER, Role.MERCHANT, Role.COURIER, Role.DISPATCHER}) {
            User caller = user(10L, role.name().toLowerCase() + "@example.com", role,
                UserStatus.ACTIVE);
            when(userRepository.findById(10L)).thenReturn(Optional.of(caller));

            mockMvc.perform(post("/api/admin/users")
                    .header("Authorization", "Bearer " + jwtService.generateToken(caller))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"name":"X","email":"x@example.com","password":"long-password-123",
                         "role":"ADMIN"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access is denied"));
        }
        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
        verify(userRepository, never()).save(any());
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Authentication is required"));
    }

    @Test
    void protectedEndpointAcceptsValidTokenAndReturnsOnlyPublicData() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Test User"))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.phone").value((String) null))
            .andExpect(jsonPath("$.address").value((String) null))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.length()").value(8))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void invalidBearerTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
    }

    @Test
    void expiredBearerTokenIsRejected() throws Exception {
        String secret = Base64.getEncoder().encodeToString(new byte[] {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31
        });
        String token = new JwtService(secret, 1).generateToken(user);
        Thread.sleep(10);

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
    }

    @Test
    void healthIsPublicAndShowsNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otherActuatorEndpointsAreNeitherPublicNorExposed() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/info", "/actuator/beans"}) {
            mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
        }
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        mockMvc.perform(get("/actuator/env")
                .header("Authorization", "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isNotFound());
    }

    @Test
    void googleSignInReportsUnavailableWhenNotConfigured() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google")
                .param("client_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl(
                "http://localhost:3000/auth/google/callback#error=google_unavailable"));
        mockMvc.perform(get("/login/oauth2/code/google").param("code", "c").param("state", "s"))
            .andExpect(redirectedUrl(
                "http://localhost:3000/auth/google/callback#error=google_unavailable"));
    }

    @Test
    void googleExchangeIsPublicAndValidatesItsInput() throws Exception {
        mockMvc.perform(post("/api/auth/google/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"\",\"verifier\":\"short\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));
    }

    @Test
    void updateProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"New Name","phone":"+1234567890","address":"123 Main St"}
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Authentication is required"));
    }

    @Test
    void updateProfileUpdatesAllowedFieldsAndReturnsProfile() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Updated Name","phone":"+1234567890","address":"123 Main St"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.phone").value("+1234567890"))
            .andExpect(jsonPath("$.address").value("123 Main St"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
            .andExpect(jsonPath("$.length()").value(8));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Updated Name");
        assertThat(saved.getValue().getPhone()).isEqualTo("+1234567890");
        assertThat(saved.getValue().getAddress()).isEqualTo("123 Main St");
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void updateProfileIgnoresClientSuppliedIdEmailRoleAndStatus() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id": 999,
                      "email": "hacked@example.com",
                      "role": "ADMIN",
                      "status": "DISABLED",
                      "name": "Updated Name",
                      "phone": "+1234567890",
                      "address": "123 Main St"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Updated Name"))
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(1L);
        assertThat(saved.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void updateProfileValidatesInput() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        // Blank name
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"","phone":"+1234567890","address":"123 Main St"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));

        // Name too long (> 100)
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"%s\",\"phone\":\"123\",\"address\":\"abc\"}", "a".repeat(101))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));

        // Phone too long (> 30)
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Valid\",\"phone\":\"%s\",\"address\":\"abc\"}", "1".repeat(31))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));

        // Address too long (> 500)
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + jwtService.generateToken(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Valid\",\"phone\":\"123\",\"address\":\"%s\"}", "x".repeat(501))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));

        verify(userRepository, never()).save(any());
    }

    private User user(long id, String email, Role role, UserStatus status) {
        User created = new User("Test User", email,
            passwordEncoder.encode("password123"), role, status);
        ReflectionTestUtils.setField(created, "id", id);
        ReflectionTestUtils.setField(created, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        return created;
    }
}
