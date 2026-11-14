package com.swiftdrop.parcel;

import com.swiftdrop.parcel.entity.*;
import com.swiftdrop.parcel.repository.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "PARCEL_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class ParcelQueryApiTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired ParcelRepository parcels;
    @Autowired ParcelStatusHistoryRepository histories;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    private Parcel parcel(long sender, ParcelStatus status, int minute) {
        Parcel p = new Parcel();
        p.setTrackingNumber("SD" + UUID.randomUUID().toString().replace("-", ""));
        p.setSenderId(sender); p.setCreatedBy(sender);
        p.setReceiverName("Receiver"); p.setReceiverPhone("0771234567");
        p.setPickupAddress("Colombo"); p.setDeliveryAddress("Kandy");
        p.setWeight(new BigDecimal("2.500")); p.setCharge(new BigDecimal("450.00"));
        p.setSize("MEDIUM"); p.setParcelType("STANDARD"); p.setStatus(status);
        p.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(minute * 60L));
        return parcels.saveAndFlush(p);
    }

    private String token(long user, String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject(Long.toString(user)).claim("role", role)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))).compact();
    }

    private ResultActions read(String path, long user, String role) throws Exception {
        entityManager.flush();
        entityManager.clear(); // Requests must hydrate entities from PostgreSQL.
        return mvc.perform(get(path).header("Authorization", token(user, role)));
    }

    @Test void customerRetrievesCorrectParcelById() throws Exception {
        Parcel p = parcel(42, ParcelStatus.CREATED, 0);
        read("/api/parcels/" + p.getId(), 42, "CUSTOMER")
            .andExpect(status().isOk())
            .andExpect(jsonPath("id").value(p.getId().toString()))
            .andExpect(jsonPath("trackingNumber").value(p.getTrackingNumber()))
            .andExpect(jsonPath("status").value("CREATED"))
            .andExpect(jsonPath("senderId").value(42));
    }

    @Test void customerListingContainsOnlyOwnedParcelsNewestFirst() throws Exception {
        Parcel older = parcel(42, ParcelStatus.CREATED, 1);
        parcel(77, ParcelStatus.CREATED, 3);
        Parcel newer = parcel(42, ParcelStatus.CREATED, 2);
        read("/api/parcels/my", 42, "CUSTOMER")
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("content[0].id").value(newer.getId().toString()))
            .andExpect(jsonPath("content[1].id").value(older.getId().toString()));
    }

    @Test void merchantListingAndDetailsContainOnlyOwnedMerchantParcels() throws Exception {
        Parcel older = parcel(51, ParcelStatus.CREATED, 1);
        older.setMerchantId(51L);
        parcels.saveAndFlush(older);
        Parcel other = parcel(77, ParcelStatus.CREATED, 3);
        other.setMerchantId(77L);
        parcels.saveAndFlush(other);
        Parcel newer = parcel(51, ParcelStatus.IN_TRANSIT, 2);
        newer.setMerchantId(51L);
        parcels.saveAndFlush(newer);

        read("/api/parcels/merchant", 51, "MERCHANT")
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("content[0].id").value(newer.getId().toString()))
            .andExpect(jsonPath("content[1].id").value(older.getId().toString()));
        read("/api/parcels/" + newer.getId(), 51, "MERCHANT")
            .andExpect(status().isOk()).andExpect(jsonPath("merchantId").value(51));
        history(newer, ParcelStatus.CREATED, ParcelStatus.IN_TRANSIT, 4);
        read("/api/parcels/" + newer.getId() + "/history", 51, "MERCHANT")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].newStatus").value("IN_TRANSIT"));
        read("/api/parcels/" + other.getId(), 51, "MERCHANT")
            .andExpect(status().isNotFound());
        read("/api/parcels/" + other.getId() + "/history", 51, "MERCHANT")
            .andExpect(status().isNotFound());
    }

    @Test void customerCannotUseMerchantListing() throws Exception {
        read("/api/parcels/merchant", 42, "CUSTOMER")
            .andExpect(status().isForbidden());
    }

    private void history(Parcel p, ParcelStatus previous, ParcelStatus next, int minute) {
        var entry = histories.saveAndFlush(new ParcelStatusHistory(p, previous, next, 42L, "Timeline"));
        jdbc.update("UPDATE parcel_status_history SET changed_at = ? WHERE id = ?",
            Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(minute * 60L)), entry.getId());
    }

    @Test void historyIsChronologicalRegardlessOfInsertionOrder() throws Exception {
        Parcel p = parcel(42, ParcelStatus.IN_TRANSIT, 0);
        history(p, ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, 3);
        history(p, null, ParcelStatus.CREATED, 1);
        history(p, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, 2);
        read("/api/parcels/" + p.getId() + "/history", 42, "CUSTOMER")
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].newStatus").value("CREATED"))
            .andExpect(jsonPath("$[1].newStatus").value("PICKED_UP"))
            .andExpect(jsonPath("$[2].newStatus").value("IN_TRANSIT"))
            .andExpect(jsonPath("$[0].changedAt").value("2026-01-01T00:01:00Z"))
            .andExpect(jsonPath("$[2].changedAt").value("2026-01-01T00:03:00Z"));
    }

    @Test void staffStatusFilterReturnsOnlyMatchingParcelsOldestFirst() throws Exception {
        Parcel newer = parcel(42, ParcelStatus.CREATED, 3);
        parcel(42, ParcelStatus.PICKED_UP, 2);
        Parcel older = parcel(77, ParcelStatus.CREATED, 1);
        read("/api/parcels?status=CREATED", 88, "DISPATCHER")
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("content[0].id").value(older.getId().toString()))
            .andExpect(jsonPath("content[1].id").value(newer.getId().toString()));
    }

    @Test void paginationReturnsCorrectContentAndTotals() throws Exception {
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) ids.add(parcel(42, ParcelStatus.CREATED, i).getId());
        parcel(77, ParcelStatus.CREATED, 6);
        read("/api/parcels/my?page=1&size=2", 42, "CUSTOMER")
            .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(2))
            .andExpect(jsonPath("totalElements").value(5)).andExpect(jsonPath("totalPages").value(3))
            .andExpect(jsonPath("number").value(1)).andExpect(jsonPath("size").value(2))
            .andExpect(jsonPath("content[0].id").value(ids.get(2).toString()))
            .andExpect(jsonPath("content[1].id").value(ids.get(1).toString()));
        read("/api/parcels/my?page=2&size=2", 42, "CUSTOMER")
            .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(1))
            .andExpect(jsonPath("content[0].id").value(ids.get(0).toString()));
        read("/api/parcels/my?page=3&size=2", 42, "CUSTOMER")
            .andExpect(status().isOk()).andExpect(jsonPath("content.length()").value(0))
            .andExpect(jsonPath("totalElements").value(5));
    }

    @Test void otherCustomersCannotReadDetailsOrHistory() throws Exception {
        Parcel p = parcel(42, ParcelStatus.CREATED, 0);
        history(p, null, ParcelStatus.CREATED, 0);
        for (String suffix : new String[]{"", "/history"}) {
            read("/api/parcels/" + p.getId() + suffix, 77, "CUSTOMER")
                .andExpect(status().isNotFound()).andExpect(jsonPath("error").value("Parcel not found"));
        }
    }

    @Test void dispatcherCanReadAnotherCustomersParcel() throws Exception {
        Parcel p = parcel(42, ParcelStatus.CREATED, 0);
        read("/api/parcels/" + p.getId(), 88, "DISPATCHER")
            .andExpect(status().isOk()).andExpect(jsonPath("id").value(p.getId().toString()));
    }

    @Test void unauthenticatedRequestsReturnUnauthorized() throws Exception {
        Parcel p = parcel(42, ParcelStatus.CREATED, 0);
        for (String path : new String[]{"/api/parcels/my", "/api/parcels?status=CREATED",
                "/api/parcels/" + p.getId(), "/api/parcels/" + p.getId() + "/history"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test void datasourceUsesOnlyTheDisposableContainer() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(postgres.getJdbcUrl());
            assertThat(connection.getCatalog()).isEqualTo(postgres.getDatabaseName());
        }
    }
}
