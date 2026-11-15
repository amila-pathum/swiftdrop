package com.swiftdrop.identity_service.merchantintegration;

import com.swiftdrop.identity_service.config.SecurityConfig;
import com.swiftdrop.identity_service.entity.*;
import com.swiftdrop.identity_service.repository.UserRepository;
import com.swiftdrop.identity_service.security.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers=MerchantTokenExchangeController.class, properties={
    "jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=", "jwt.expiration-ms=900000",
    "merchant.order.service-token=integration-test-service-token-32-characters" })
@Import({SecurityConfig.class, MerchantTokenExchangeSecurity.class, JwtService.class, JwtAuthenticationFilter.class})
class MerchantTokenExchangeTest {
    static final String PATH="/internal/merchant-order-tokens";
    static final String TOKEN="integration-test-service-token-32-characters";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean UserRepository users;
    User merchant;
    @BeforeEach void setup() {
        merchant=mock(User.class);
        when(merchant.getId()).thenReturn(42L); when(merchant.getEmail()).thenReturn("merchant@example.test");
        when(merchant.getRole()).thenReturn(Role.MERCHANT); when(merchant.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(merchant.getTokenVersion()).thenReturn(3); when(users.findById(42L)).thenReturn(Optional.of(merchant));
    }
    @Test void missingOrWrongServiceCredentialCannotMintTokens() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("X-SwiftDrop-Merchant-Token","wrong")
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }
    @Test void claimsComeFromTheRealAccountAndLifetimeIsOnlySixtySeconds() throws Exception {
        var result=mvc.perform(post(PATH).header("X-SwiftDrop-Merchant-Token",TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42,\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andReturn();
        var body=mapper.readTree(result.getResponse().getContentAsString());
        var claims=Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))).build()
                .parseSignedClaims(body.get("accessToken").asText()).getPayload();
        assertEquals("42",claims.getSubject()); assertEquals("MERCHANT",claims.get("role"));
        assertEquals("ACTIVE",claims.get("status")); assertEquals(3,claims.get("ver",Integer.class));
        assertEquals("MERCHANT_ORDER_INTEGRATION",claims.get("purpose"));
        assertEquals(60_000,claims.getExpiration().getTime()-claims.getIssuedAt().getTime());
        assertTrue(claims.getAudience().containsAll(List.of("parcel-service","pricing-service")));
        assertTrue(result.getResponse().getHeader("Cache-Control").contains("no-store"));
    }
    @Test void pendingDisabledSuspendedAndNonMerchantAccountsCannotReceiveDelegation() throws Exception {
        for(var accountStatus:List.of(UserStatus.PENDING_APPROVAL,UserStatus.SUSPENDED,UserStatus.DISABLED)) {
            when(merchant.getStatus()).thenReturn(accountStatus);
            exchange().andExpect(status().isForbidden());
        }
        when(merchant.getStatus()).thenReturn(UserStatus.ACTIVE); when(merchant.getRole()).thenReturn(Role.ADMIN);
        exchange().andExpect(status().isForbidden());
        when(users.findById(42L)).thenReturn(Optional.empty()); exchange().andExpect(status().isForbidden());
    }
    private org.springframework.test.web.servlet.ResultActions exchange() throws Exception {
        return mvc.perform(post(PATH).header("X-SwiftDrop-Merchant-Token",TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":42}"));
    }
}
