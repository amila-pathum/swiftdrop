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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "PARCEL_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
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
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        String number = "SD" + UUID.randomUUID().toString().replace("-", "");
        when(tracking.generate()).thenReturn(number);
        doThrow(new org.springframework.dao.DataIntegrityViolationException("History write failed"))
                .when(history).save(any());
        var request = new com.swiftdrop.parcel.dto.request.CreateParcelRequest(
                "Nimal", "0771234567", "Colombo", "Kandy", new BigDecimal("2.500"), "MEDIUM", "STANDARD");
        assertThatThrownBy(() -> creation.createCustomerParcel(request, UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(parcels.count()).isEqualTo(before);
        assertThat(parcels.findByTrackingNumber(number)).isEmpty();
    }

    @Test
    void createsOrderAndHistoryUsingAuthenticatedCustomerAndServerPrice() throws Exception {
        UUID customer = UUID.randomUUID();
        String number = "SD" + UUID.randomUUID().toString().replace("-", "");
        when(pricing.quote(any())).thenReturn(new BigDecimal("450.00"));
        when(tracking.generate()).thenReturn(number);
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject(customer.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated()).andExpect(jsonPath("trackingNumber").value(number))
            .andExpect(jsonPath("charge").value(450.00)).andExpect(jsonPath("status").value("CREATED"))
            .andExpect(jsonPath("createdAt").isNotEmpty());
        var parcel = parcels.findByTrackingNumber(number).orElseThrow();
        assertThat(parcel.getSenderId()).isEqualTo(customer);
        assertThat(parcel.getCreatedBy()).isEqualTo(customer);
        assertThat(parcel.getReceiverName()).isEqualTo("Nimal");
        assertThat(parcel.getPickupAddress()).isEqualTo("Colombo");
        var entries = history.findByParcelIdOrderByChangedAtAsc(parcel.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().getPreviousStatus()).isNull();
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

    @Test
    void dispatchCanReadCreatedParcelProjection() throws Exception {
        mvc.perform(get("/api/parcels?status=CREATED").with(jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/parcels").with(jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
    void rejectsInvalidSubjects(String subject) throws Exception {
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(pricing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0001", "10000000", "1.1234"})
    void rejectsInvalidWeights(String weight) throws Exception {
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("2.500", weight)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(pricing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "10000000000"})
    void rejectsInvalidQuotesBeforeSaving(String charge) throws Exception {
        long before = parcels.count();
        when(pricing.quote(any())).thenReturn(new BigDecimal(charge));
        mvc.perform(post("/api/parcels").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadGateway());
        assertThat(parcels.count()).isEqualTo(before);
    }
}
