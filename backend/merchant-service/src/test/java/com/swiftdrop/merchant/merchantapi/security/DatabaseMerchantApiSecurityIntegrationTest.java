package com.swiftdrop.merchant.merchantapi.security;

import com.swiftdrop.merchant.service.MerchantApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class DatabaseMerchantApiSecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    private static final String ORDERS =
            "/api/merchants/integration/orders";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MerchantApiKeyService keyService;

    private Long userId;

    @BeforeEach
    void createApprovedMerchant() {
        jdbc.update("DELETE FROM merchant_api_keys");
        jdbc.update("DELETE FROM merchant_webhook_settings");
        jdbc.update("DELETE FROM merchants");

        userId = 9001L;

        jdbc.update("""
                INSERT INTO merchants (
                    user_id, business_name, registration_number,
                    contact_name, email, phone, address, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                "Integration Test Shop",
                "TEST-REG-9001",
                "Test Merchant",
                "merchant@example.test",
                "0771234567",
                "Test address",
                "APPROVED"
        );
    }

    @Test
    void missingAndUnknownKeysAreUnauthorized() throws Exception {
        mockMvc.perform(post(ORDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(ORDERS)
                        .header("X-API-Key", "unknown-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuedKeyReachesRequestValidation() throws Exception {
        String key = issueKey();

        // Empty input must fail validation after authentication succeeds.
        mockMvc.perform(post(ORDERS)
                        .header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalChangesAreCheckedOnTheNextRequest() throws Exception {
        String key = issueKey();

        jdbc.update(
                "UPDATE merchants SET status = 'PENDING' WHERE user_id = ?",
                userId
        );

        mockMvc.perform(post(ORDERS)
                        .header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokedKeyIsUnauthorized() throws Exception {
        String key = issueKey();

        jdbc.update("""
                UPDATE merchant_api_keys
                SET revoked_at = CURRENT_TIMESTAMP
                WHERE key_hash = ?
                """, MerchantApiKeyService.sha256(key));

        mockMvc.perform(post(ORDERS)
                        .header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String issueKey() {
        // Read the plaintext key from Member 8's actual creation response.
        var issued = keyService.create(userId, "Integration test");
        return issued.secretKey();
    }
}