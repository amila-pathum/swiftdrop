package com.swiftdrop.identity_service;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
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
        User pending = user(3L, "pending@example.com", Role.CUSTOMER,
            UserStatus.PENDING_APPROVAL);
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"pending@example.com","password":"password123"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("This account is not permitted to sign in"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
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
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.length()").value(6))
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

    private User user(long id, String email, Role role, UserStatus status) {
        User created = new User("Test User", email,
            passwordEncoder.encode("password123"), role, status);
        ReflectionTestUtils.setField(created, "id", id);
        ReflectionTestUtils.setField(created, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        return created;
    }
}
