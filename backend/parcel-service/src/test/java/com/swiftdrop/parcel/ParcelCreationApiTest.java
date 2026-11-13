package com.swiftdrop.parcel;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.service.TrackingNumberGenerator;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "PARCEL_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers
class ParcelCreationApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    ParcelStatusHistoryRepository history;
    @Autowired com.swiftdrop.parcel.service.ParcelCreationService creation;
    @MockitoBean PricingClient pricing;
    @MockitoBean TrackingNumberGenerator tracking;

    private static final String BODY = """
        {"receiverName":" Nimal ","receiverPhone":"0771234567",
         "pickupAddress":" Colombo ","deliveryAddress":"Kandy",
         "weight":2.500,"size":"MEDIUM","parcelType":"STANDARD"}
        """;

    @Test
    void historyFailureRollsBackTheParcelInsert() {
        long before = parcels.count();
        long historyBefore = history.count();
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        String number = "SD" + UUID.randomUUID().toString().replace("-", "");
        when(tracking.generate()).thenReturn(number);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("History write failed"))
                .when(history).save(any());
        var request = new com.swiftdrop.parcel.dto.request.CreateParcelRequest(
                "Nimal", "0771234567", "Colombo", "Kandy", new BigDecimal("2.500"), "MEDIUM", "STANDARD");
        assertThatThrownBy(() -> creation.createCustomerParcel(request, 42L))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
        assertThat(parcels.findByTrackingNumber(number)).isEmpty();
    }

    @Test
    void createsOrderAndHistoryUsingAuthenticatedCustomerAndServerPrice() throws Exception {
        long parcelCount = parcels.count();
        long historyCount = history.count();
        Long customer = 42L;
        String number = "SD" + UUID.randomUUID().toString().replace("-", "");
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        when(tracking.generate()).thenReturn(number);
        mvc.perform(post("/api/parcels").header("Authorization", customerToken(customer))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("trackingNumber").value(number))
            .andExpect(jsonPath("charge").value(450.00)).andExpect(jsonPath("status").value("CREATED"))
            .andExpect(jsonPath("createdAt").isNotEmpty());
        var parcel = parcels.findByTrackingNumber(number).orElseThrow();
        assertThat(parcels.count()).isEqualTo(parcelCount + 1);
        assertThat(history.count()).isEqualTo(historyCount + 1);
        assertThat(parcel.getStatus()).isEqualTo(com.swiftdrop.parcel.entity.ParcelStatus.CREATED);
        assertThat(parcel.getCharge()).isEqualByComparingTo("450.00");
        assertThat(parcel.getSenderId()).isEqualTo(customer);
        assertThat(parcel.getCreatedBy()).isEqualTo(customer);
        assertThat(parcel.getReceiverName()).isEqualTo("Nimal");
        assertThat(parcel.getPickupAddress()).isEqualTo("Colombo");
        var entries = history.findByParcelIdOrderByChangedAtAsc(parcel.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getPreviousStatus()).isNull();
        assertThat(entries.getFirst().getNewStatus()).isEqualTo(com.swiftdrop.parcel.entity.ParcelStatus.CREATED);
        assertThat(entries.getFirst().getChangedBy()).isEqualTo(customer);
    }

    @Test
    void requiresAuthenticationAndCustomerRole() throws Exception {
        mvc.perform(post("/api/parcels").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/parcels").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MERCHANT")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        verifyNoInteractions(pricing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "1-1-1-1-1", "0", "-1", "01", "+1", "9223372036854775808", "550e8400-e29b-41d4-a716-446655440000"})
    void rejectsInvalidSubjects(String subject) throws Exception {
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(pricing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.0001", "10000000", "1.1234"})
    void rejectsInvalidWeights(String weight) throws Exception {
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject("42"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("2.500", weight)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(pricing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "10000000000"})
    void rejectsInvalidQuotesBeforeSaving(String charge) throws Exception {
        long before = parcels.count();
        long historyBefore = history.count();
        when(pricing.quote(any())).thenReturn(new BigDecimal(charge));
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject("42"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadGateway());
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "receiverName,", "receiverName,   ", "receiverPhone,abc",
        "receiverPhone,123", "pickupAddress,", "deliveryAddress,",
        "size,INVALID", "parcelType,INVALID"
    })
    void rejectsInvalidDetails(String field, String value) throws Exception {
        var mapper = new tools.jackson.databind.ObjectMapper();
        var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(BODY);
        if (value == null) body.remove(field); else body.put(field, value);
        long before = parcels.count();
        long historyBefore = history.count();
        customerPost(mapper.writeValueAsString(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(pricing);
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
    }

    @Test
    void unavailablePricingCreatesNoRecords() throws Exception {
        long before = parcels.count();
        long historyBefore = history.count();
        when(pricing.quote(any())).thenThrow(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Pricing service unavailable"));
        customerPost(BODY).andExpect(status().isServiceUnavailable());
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
    }

    @Test
    void missingPriceCreatesNoRecords() throws Exception {
        long before = parcels.count();
        long historyBefore = history.count();
        when(pricing.quote(any())).thenReturn(null);
        customerPost(BODY).andExpect(status().isBadGateway());
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
    }

    @Test
    void duplicateTrackingNumberFailsWithoutAdditionalRecords() throws Exception {
        String number = "SD" + UUID.randomUUID().toString().replace("-", "");
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        when(tracking.generate()).thenReturn(number);
        customerPost(BODY).andExpect(status().isCreated());
        long before = parcels.count();
        long historyBefore = history.count();
        customerPost(BODY).andExpect(status().isConflict())
            .andExpect(jsonPath("error").value("Parcel could not be created due to a data conflict"));
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
        assertThat(parcels.findByTrackingNumber(number)).isPresent();
    }

    @Test
    void historyFailureThroughApiRollsBackEverything() throws Exception {
        long before = parcels.count();
        long historyBefore = history.count();
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        when(tracking.generate()).thenReturn("SD" + UUID.randomUUID().toString().replace("-", ""));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("History insert failed"))
            .when(history).save(any());
        customerPost(BODY).andExpect(status().isConflict());
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(history.count()).isEqualTo(historyBefore);
    }

    @Test
    void frontendCannotOverridePrice() throws Exception {
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        String number = "SD" + UUID.randomUUID().toString().replace("-", "");
        when(tracking.generate()).thenReturn(number);
        customerPost(BODY.replace("\"weight\":2.500", "\"charge\":0.01,\"weight\":2.500"))
            .andExpect(status().isCreated()).andExpect(jsonPath("charge").value(450.00));
        assertThat(parcels.findByTrackingNumber(number).orElseThrow().getCharge())
            .isEqualByComparingTo("450.00");
    }

    private org.springframework.test.web.servlet.ResultActions customerPost(String body) throws Exception {
        return mvc.perform(post("/api/parcels").header("Authorization", customerToken(42L))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private String customerToken(Long customer) {
        var key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(java.util.Base64.getDecoder()
            .decode("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="));
        var now = java.time.Instant.now();
        return "Bearer " + io.jsonwebtoken.Jwts.builder().subject(customer.toString())
            .claim("role", "CUSTOMER").issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(now.plusSeconds(300))).signWith(key).compact();
    }
}
