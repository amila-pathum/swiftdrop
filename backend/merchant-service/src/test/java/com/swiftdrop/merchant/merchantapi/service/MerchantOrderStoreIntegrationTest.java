package com.swiftdrop.merchant.merchantapi.service;

import com.swiftdrop.merchant.merchantapi.dto.MerchantOrderResponse;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class MerchantOrderStoreIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");
    MerchantOrderStore store;
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP TABLE IF EXISTS merchant_integration_orders");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V4__create_merchant_integration_orders.sql")).execute(ds);
        store = new MerchantOrderStore(jdbc);
    }
    @Test void concurrentReservationHasExactlyOneWinner() throws Exception {
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = IntStream.range(0, 12).mapToObj(i -> (Callable<Boolean>) () -> store.reserve("1","SHOP-1","hash")).toList();
            int wins = 0;
            for (var result : pool.invokeAll(jobs)) if (result.get()) wins++;
            assertEquals(1,wins);
        }
        assertNull(store.reservation("1","SHOP-1").result());
        assertFalse(store.reserve("1","SHOP-1","changed"));
        assertEquals("hash",store.reservation("1","SHOP-1").hash());
    }
    @Test void completedOrdersAndReferencesAreMerchantScoped() {
        assertTrue(store.reserve("1","SHOP-1","hash"));
        assertTrue(store.reserve("2","SHOP-1","hash2"));
        var response = new MerchantOrderResponse("SHOP-1","SD-1","CREATED");
        store.complete("1",response);
        assertEquals(response,store.reservation("1","SHOP-1").result());
        assertEquals(response,store.detail("1","SD-1").orElseThrow());
        assertTrue(store.detail("2","SD-1").isEmpty());
        assertEquals(1,store.list("1",0,20).total());
        assertEquals(0,store.list("2",0,20).total());
    }
}
