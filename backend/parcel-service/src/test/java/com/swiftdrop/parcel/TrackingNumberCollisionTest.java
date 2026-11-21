package com.swiftdrop.parcel;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.dto.request.CreateParcelRequest;
import com.swiftdrop.parcel.merchantintegration.MerchantIntegrationController;
import com.swiftdrop.parcel.merchantintegration.MerchantIntegrationService;
import com.swiftdrop.parcel.service.ParcelCreationService;
import com.swiftdrop.parcel.service.TrackingNumberGenerator;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"PARCEL_DB_PASSWORD=test", "swiftdrop.events.publisher-enabled=false",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@Testcontainers
class TrackingNumberCollisionTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    @Autowired ParcelCreationService creation;
    @Autowired MerchantIntegrationService merchants;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @MockitoBean PricingClient pricing;
    @MockitoBean TrackingNumberGenerator tracking;
    final CreateParcelRequest request = new CreateParcelRequest("Receiver", "0771234567",
        "10 Main Street", null, "Colombo", "20 Lake Road", null, "Kandy",
        new BigDecimal("1.250"), "MEDIUM", "STANDARD");

    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE merchant_order_idempotency,parcel_event_outbox,parcel_status_history,parcels CASCADE");
        when(pricing.quote(any(), anyString())).thenReturn(new BigDecimal("450.00"));
    }

    @Test void collisionRetriesWithoutRepeatingPricingHistoryOrEvent() {
        when(tracking.generate()).thenReturn("SD10000000", "SD10000000", "SD10000001");
        var first = creation.createCustomerParcel(request, 42L, "token");
        var second = creation.createCustomerParcel(request, 42L, "token");
        assertNotEquals(first.trackingNumber(), second.trackingNumber());
        assertCounts(2);
        verify(pricing, times(2)).quote(any(), anyString());
        verify(tracking, times(3)).generate();
    }

    @Test void exhaustionRollsBackMerchantReservationAndRetryIsIdempotent() throws Exception {
        when(tracking.generate()).thenReturn("SD10000000");
        creation.createCustomerParcel(request, 42L, "token");
        var order = mapper.readValue(MerchantOrderIntegrationTest.BODY, MerchantIntegrationController.Request.class);
        var error = assertThrows(ResponseStatusException.class, () -> merchants.create(51L, "token", order));
        assertEquals(503, error.getStatusCode().value());
        verify(tracking, times(11)).generate();
        assertCounts(1);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM merchant_order_idempotency", Integer.class));
        when(tracking.generate()).thenReturn("SD10000000", "SD10000001");
        var created = merchants.create(51L, "token", order);
        assertEquals(created.parcelId(), merchants.create(51L, "token", order).parcelId());
        assertCounts(2);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM merchant_order_idempotency", Integer.class));
    }

    @Test void simultaneousBookingsWithSameCandidateBothSucceed() throws Exception {
        var barrier = new CyclicBarrier(2);
        var calls = new AtomicInteger();
        when(tracking.generate()).thenAnswer(inv -> {
            int call = calls.getAndIncrement();
            if (call < 2) {
                barrier.await(10, TimeUnit.SECONDS);
                return "SD10000000";
            }
            return "SD10000001";
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> creation.createCustomerParcel(request, 42L, "token"));
            var second = pool.submit(() -> creation.createMerchantParcel(request, 51L, "token"));
            assertNotEquals(first.get(20, TimeUnit.SECONDS).trackingNumber(),
                second.get(20, TimeUnit.SECONDS).trackingNumber());
        }
        assertCounts(2);
    }

    @Test void realGeneratorCreatesDistinctPersistedBookings() {
        var generator = new TrackingNumberGenerator();
        when(tracking.generate()).thenAnswer(inv -> generator.generate());
        var first = creation.createCustomerParcel(request, 42L, "token");
        var second = creation.createCustomerParcel(request, 42L, "token");
        assertTrue(first.trackingNumber().matches("SD[0-9]{8}"));
        assertNotEquals(first.trackingNumber(), second.trackingNumber());
        assertCounts(2);
    }

    @Test void unrelatedConstraintFailureIsNotRetried() {
        when(tracking.generate()).thenReturn("SD10000000");
        var invalid = new CreateParcelRequest("Receiver", "0771234567", "Pickup", null, "Colombo",
            "Delivery", null, "Kandy", BigDecimal.ZERO, "MEDIUM", "STANDARD");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            () -> creation.createCustomerParcel(invalid, 42L, "token"));
        verify(tracking).generate();
        assertCounts(0);
    }

    private void assertCounts(int expected) {
        for (String table : java.util.List.of("parcels", "parcel_status_history", "parcel_event_outbox")) {
            assertEquals(expected, jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class), table);
        }
    }
}
