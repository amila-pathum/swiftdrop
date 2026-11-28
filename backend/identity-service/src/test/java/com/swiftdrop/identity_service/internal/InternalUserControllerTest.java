package com.swiftdrop.identity_service.internal;

import com.swiftdrop.identity_service.config.SecurityConfig;
import com.swiftdrop.identity_service.entity.*;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = InternalUserController.class, properties = {
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", "jwt.expiration-ms=900000",
    "swiftdrop.internal.service-token=internal-test-service-token-32-characters" })
@Import({SecurityConfig.class, InternalServiceSecurity.class, JwtService.class, JwtAuthenticationFilter.class})
class InternalUserControllerTest {
    static final String TOKEN = "internal-test-service-token-32-characters";
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;

    User user(long id, String email, Role role) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id); when(user.getName()).thenReturn("Nimal");
        when(user.getEmail()).thenReturn(email); when(user.getRole()).thenReturn(role);
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return user;
    }

    @Test void requiresTheServiceTokenNotAUserToken() throws Exception {
        mvc.perform(get("/internal/users/42")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/42").header(InternalServiceSecurity.HEADER, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users?role=DISPATCHER").header("Authorization", "Bearer something")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test void returnsContactDetailsForAUser() throws Exception {
        var customer = user(42, "nimal@example.test", Role.CUSTOMER);
        when(users.findById(42L)).thenReturn(Optional.of(customer));
        mvc.perform(get("/internal/users/42").header(InternalServiceSecurity.HEADER, TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("nimal@example.test"))
            .andExpect(jsonPath("$.role").value("CUSTOMER"))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/internal/users/7").header(InternalServiceSecurity.HEADER, TOKEN)).andExpect(status().isNotFound());
    }

    @Test void listsActiveIdsByRole() throws Exception {
        var a = user(3, "a@example.test", Role.DISPATCHER);
        var b = user(5, "b@example.test", Role.DISPATCHER);
        when(users.findByRoleAndStatusOrderByIdAsc(Role.DISPATCHER, UserStatus.ACTIVE)).thenReturn(List.of(a, b));
        mvc.perform(get("/internal/users?role=DISPATCHER&status=ACTIVE").header(InternalServiceSecurity.HEADER, TOKEN))
            .andExpect(status().isOk()).andExpect(content().json("[3,5]"));
    }
}
