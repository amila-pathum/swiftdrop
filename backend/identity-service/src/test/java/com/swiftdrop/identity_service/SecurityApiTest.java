package com.swiftdrop.identity_service;

import com.swiftdrop.identity_service.entity.Role;
import com.swiftdrop.identity_service.entity.User;
import com.swiftdrop.identity_service.entity.UserStatus;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
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
@Import(SecurityApiTest.RoleRestrictedTestEndpoints.class)
class SecurityApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Test User", "user@example.com",
            passwordEncoder.encode("password123"), Role.CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
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

    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT", "COURIER", "DISPATCHER"})
    void publicRegistrationRejectsPrivilegedRole(String role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"New User","email":"new@example.com",
                     "password":"password123","role":"%s"}
                    """.formatted(role)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid request data"));
    }

    @Test
    void duplicateRegistrationStillReturnsConflict() throws Exception {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Test User","email":"user@example.com","password":"password123"}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("Email is already registered"));
    }

    @Test
    void loginIsPublic() throws Exception {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"user@example.com","password":"password123"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
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
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void customerCanAccessCustomerAuthorizedResource() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/test/rbac/customer")
                .header("Authorization", "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isOk())
            .andExpect(content().string("customer"));
    }

    @Test
    void customerCannotAccessMerchantAuthorizedResource() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/test/rbac/merchant")
                .header("Authorization", "Bearer " + jwtService.generateToken(user)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Access is denied"));
    }

    @Test
    void inactiveAccountCannotUsePreviouslyIssuedToken() throws Exception {
        String token = jwtService.generateToken(user);
        ReflectionTestUtils.setField(user, "status", UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/test/rbac/customer")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Invalid or expired access token"));
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RoleRestrictedTestEndpoints {

        @RestController
        static class Controller {

            @GetMapping("/test/rbac/customer")
            @PreAuthorize("hasRole('CUSTOMER')")
            String customer() {
                return "customer";
            }

            @GetMapping("/test/rbac/merchant")
            @PreAuthorize("hasRole('MERCHANT')")
            String merchant() {
                return "merchant";
            }
        }
    }
}
