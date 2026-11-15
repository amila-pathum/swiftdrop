package com.swiftdrop.merchant.controller;

import com.swiftdrop.merchant.config.SecurityConfig;
import com.swiftdrop.merchant.dto.response.MerchantResponse;
import com.swiftdrop.merchant.entity.MerchantStatus;
import com.swiftdrop.merchant.service.MerchantService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantController.class)
@Import({SecurityConfig.class, MerchantControllerErrors.class})
@TestPropertySource(properties = "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
class MerchantApprovalControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MerchantService service;
    static final String PATH = "/api/merchants/admin/2/approve";
    String token(String role, String status) {
        return "Bearer " + Jwts.builder().subject("99").claim("role", role).claim("status", status)
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")))
                .compact();
    }
    @Test void onlyAnActiveAdministratorMayApprove() throws Exception {
        mvc.perform(patch(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(patch(PATH).header("Authorization", token("MERCHANT", "ACTIVE"))).andExpect(status().isForbidden());
        mvc.perform(patch(PATH).header("Authorization", token("ADMIN", "PENDING_APPROVAL"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void forwardsTheOriginalAdminAuthorizationAndMerchantId() throws Exception {
        String authorization = token("ADMIN", "ACTIVE");
        when(service.approveMerchant(2L, 99L, authorization)).thenReturn(new MerchantResponse(
                2L, "Store", "BR-1", "Owner", "owner@example.test", "0771234567", "Address", MerchantStatus.APPROVED, null, null));
        mvc.perform(patch(PATH).header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        verify(service).approveMerchant(2L, 99L, authorization);
    }
    @Test void activationOutageRemains503InsteadOfAnErrorDispatch401() throws Exception {
        String authorization = token("ADMIN", "ACTIVE");
        when(service.approveMerchant(2L, 99L, authorization)).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Account activation could not be confirmed"));
        mvc.perform(patch(PATH).header("Authorization", authorization))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("Account activation could not be confirmed"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }
}
