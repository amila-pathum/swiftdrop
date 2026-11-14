package com.swiftdrop.parcel;

import com.swiftdrop.parcel.entity.*;
import com.swiftdrop.parcel.repository.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "PARCEL_DB_PASSWORD=unused", "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ParcelTrackingApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired ParcelStatusHistoryRepository history;
    @Autowired JdbcTemplate jdbc;

    Parcel parcel() {
        Parcel p = new Parcel();
        p.setTrackingNumber("SD" + UUID.randomUUID().toString().replace("-", "").toUpperCase());
        p.setSenderId(System.nanoTime()); p.setCreatedBy(p.getSenderId());
        p.setReceiverName("Private recipient"); p.setReceiverPhone("0771234567");
        p.setPickupAddress("Private pickup address"); p.setDeliveryAddress("Private delivery address");
        p.setWeight(BigDecimal.ONE); p.setCharge(BigDecimal.TEN);
        p.setSize("SMALL"); p.setParcelType("STANDARD"); p.setStatus(ParcelStatus.IN_TRANSIT);
        return parcels.saveAndFlush(p);
    }

    @Test void publicLookupReturnsOnlyAllowedFieldsAndChronologicalRealHistory() throws Exception {
        Parcel p = parcel();
        var later = history.saveAndFlush(new ParcelStatusHistory(p, ParcelStatus.CREATED,
            ParcelStatus.PICKED_UP, p.getSenderId(), "Private internal note"));
        var earlier = history.saveAndFlush(new ParcelStatusHistory(p, null,
            ParcelStatus.CREATED, p.getSenderId(), "Private creation note"));
        jdbc.update("update parcel_status_history set changed_at = '2026-01-01T00:00:00Z' where id = ?", earlier.getId());
        jdbc.update("update parcel_status_history set changed_at = '2026-01-02T00:00:00Z' where id = ?", later.getId());
        history.saveAndFlush(new ParcelStatusHistory(parcel(), null, ParcelStatus.CREATED,
            System.nanoTime(), "Another parcel"));
        String body = mvc.perform(get("/api/parcels/track/" + p.getTrackingNumber()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.trackingNumber").value(p.getTrackingNumber()))
            .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.estimatedDeliveryDate").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.trackingHistory.length()").value(2))
            .andExpect(jsonPath("$.trackingHistory[0].status").value("CREATED"))
            .andExpect(jsonPath("$.trackingHistory[1].status").value("PICKED_UP"))
            .andExpect(jsonPath("$.trackingHistory[0].timestamp").value("2026-01-01T00:00:00Z"))
            .andReturn().getResponse().getContentAsString();
        var json = new tools.jackson.databind.ObjectMapper().readTree(body);
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("trackingNumber", "status", "createdAt",
            "estimatedDeliveryDate", "origin", "destination", "trackingHistory");
        assertThat(json.get("trackingHistory").get(0).propertyNames())
            .containsExactlyInAnyOrder("status", "description", "location", "timestamp");
        assertThat(body).doesNotContain("Private", "0771234567", p.getId().toString(), p.getSenderId().toString());
    }

    @Test void emptyHistoryIsNotFabricated() throws Exception {
        mvc.perform(get("/api/parcels/track/" + parcel().getTrackingNumber()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.trackingHistory").isEmpty());
    }

    @Test void unknownMalformedAndNonExactNumbersReturnSame404() throws Exception {
        Parcel p = parcel();
        for (String number : new String[] {"SD-UNKNOWN", "x", "a".repeat(41), p.getTrackingNumber().toLowerCase()}) {
            mvc.perform(get("/api/parcels/track/" + number)).andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"Parcel not found\"}"));
        }
    }

    @Test void missingNumberOtherPathsAndWritesRemainProtected() throws Exception {
        for (String path : new String[] {"/api/parcels/track", "/api/parcels/track/", "/api/parcels/my",
                "/api/parcels/track/SD-123456/history"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/parcels/track/SD-123456")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/parcels")).andExpect(status().isUnauthorized());
    }
}
