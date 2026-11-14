package com.swiftdrop.parcel;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.entity.ParcelStatusHistory;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "PARCEL_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.events.publisher-enabled=false",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ParcelCustomerActionsApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired ParcelStatusHistoryRepository histories;
    @Autowired JdbcTemplate jdbc;

    @Test
    void ownerCancellationPersistsHistoryAndOutboxExactlyOnce() throws Exception {
        Parcel parcel = parcel(42L, ParcelStatus.CREATED);
        long historyBefore = histories.count();
        int outboxBefore = outboxCount(parcel.getId());

        mvc.perform(patch("/api/parcels/{id}/cancel", parcel.getId())
                .header(HttpHeaders.AUTHORIZATION, token(42L, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(parcel.getId().toString()))
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        Parcel saved = parcels.findById(parcel.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(histories.count()).isEqualTo(historyBefore + 1);
        ParcelStatusHistory event = histories.findByParcelIdOrderByChangedAtAsc(parcel.getId()).getLast();
        assertThat(event.getPreviousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.getNewStatus()).isEqualTo(ParcelStatus.CANCELLED);
        assertThat(event.getChangedBy()).isEqualTo(42L);
        assertThat(outboxCount(parcel.getId())).isEqualTo(outboxBefore + 1);

        mvc.perform(patch("/api/parcels/{id}/cancel", parcel.getId())
                .header(HttpHeaders.AUTHORIZATION, token(42L, "CUSTOMER")))
            .andExpect(status().isBadRequest());
        assertThat(histories.count()).isEqualTo(historyBefore + 1);
        assertThat(outboxCount(parcel.getId())).isEqualTo(outboxBefore + 1);
    }

    @Test
    void cancellationRejectsAnotherCustomerWithoutChangingParcel() throws Exception {
        Parcel parcel = parcel(42L, ParcelStatus.CREATED);

        mvc.perform(patch("/api/parcels/{id}/cancel", parcel.getId())
                .header(HttpHeaders.AUTHORIZATION, token(77L, "CUSTOMER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Access is denied"));

        assertThat(parcels.findById(parcel.getId()).orElseThrow().getStatus())
            .isEqualTo(ParcelStatus.CREATED);
        assertThat(histories.findByParcelIdOrderByChangedAtAsc(parcel.getId())).isEmpty();
        assertThat(outboxCount(parcel.getId())).isZero();
    }

    @Test
    void deliveredOwnerDownloadsPdfButOtherAndUndeliveredCustomersCannot() throws Exception {
        Parcel delivered = parcel(42L, ParcelStatus.DELIVERED);
        histories.saveAndFlush(new ParcelStatusHistory(delivered, ParcelStatus.OUT_FOR_DELIVERY,
            ParcelStatus.DELIVERED, 88L, "Delivered to receiver"));

        byte[] pdf = mvc.perform(get("/api/parcels/{id}/receipt", delivered.getId())
                .header(HttpHeaders.AUTHORIZATION, token(42L, "CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"receipt-" + delivered.getTrackingNumber() + ".pdf\""))
            .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 8, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-1.4");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1))
            .contains(delivered.getTrackingNumber(), "DELIVERED", "450.00");

        mvc.perform(get("/api/parcels/{id}/receipt", delivered.getId())
                .header(HttpHeaders.AUTHORIZATION, token(77L, "CUSTOMER")))
            .andExpect(status().isForbidden());

        Parcel created = parcel(42L, ParcelStatus.CREATED);
        mvc.perform(get("/api/parcels/{id}/receipt", created.getId())
                .header(HttpHeaders.AUTHORIZATION, token(42L, "CUSTOMER")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Receipt is only available for delivered parcels"));
    }

    @Test
    void dispatchProjectionByIdReflectsNumericCourierAssignmentAndEnforcesRole() throws Exception {
        Parcel parcel = parcel(42L, ParcelStatus.CREATED);

        mvc.perform(patch("/api/parcels/{id}/courier", parcel.getId())
                .header(HttpHeaders.AUTHORIZATION, token(70L, "DISPATCHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courierUserId\":88}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/parcels/dispatch/{id}", parcel.getId())
                .header(HttpHeaders.AUTHORIZATION, token(70L, "DISPATCHER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.parcelId").value(parcel.getId().toString()))
            .andExpect(jsonPath("$.trackingNumber").value(parcel.getTrackingNumber()))
            .andExpect(jsonPath("$.courierUserId").value(88))
            .andExpect(jsonPath("$.courierId").doesNotExist());

        mvc.perform(get("/api/parcels/dispatch/{id}", parcel.getId())
                .header(HttpHeaders.AUTHORIZATION, token(42L, "CUSTOMER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/parcels/dispatch/{id}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, token(70L, "DISPATCHER")))
            .andExpect(status().isNotFound());
    }

    private Parcel parcel(long senderId, ParcelStatus status) {
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber("SD" + UUID.randomUUID().toString().replace("-", ""));
        parcel.setSenderId(senderId);
        parcel.setCreatedBy(senderId);
        parcel.setReceiverName("Nimal Perera");
        parcel.setReceiverPhone("0771234567");
        parcel.setPickupAddress("10 Main Street, Colombo");
        parcel.setDeliveryAddress("20 Lake Road, Kandy");
        parcel.setWeight(new BigDecimal("2.500"));
        parcel.setSize("MEDIUM");
        parcel.setParcelType("STANDARD");
        parcel.setCharge(new BigDecimal("450.00"));
        parcel.setStatus(status);
        return parcels.saveAndFlush(parcel);
    }

    private int outboxCount(UUID parcelId) {
        return jdbc.queryForObject(
            "SELECT count(*) FROM parcel_event_outbox WHERE parcel_id = ?",
            Integer.class, parcelId);
    }

    private String token(long userId, String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject(Long.toString(userId)).claim("role", role)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))).compact();
    }
}
