package com.swiftdrop.parcel;

import com.swiftdrop.parcel.entity.Parcel;
import com.swiftdrop.parcel.entity.ParcelStatus;
import com.swiftdrop.parcel.repository.ParcelRepository;
import com.swiftdrop.parcel.repository.ParcelStatusHistoryRepository;
import com.swiftdrop.parcel.service.ParcelService;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Starts the whole service against a throwaway PostgreSQL (needs Docker). */
@SpringBootTest(properties = {
    "PARCEL_DB_PASSWORD=unused-with-service-connection",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
    "management.health.rabbit.enabled=false"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ParcelServiceApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mockMvc;
    @Autowired ParcelService parcelService;
    @Autowired ParcelRepository parcels;
    @Autowired ParcelStatusHistoryRepository history;

    @Test
    void persistsParcelAndCompleteStatusHistory() {
        Long actor = 42L;
        Parcel parcel = new Parcel();
        parcel.setTrackingNumber("T-" + UUID.randomUUID());
        parcel.setSenderId(actor);
        parcel.setCreatedBy(actor);
        parcel.setReceiverName("Receiver");
        parcel.setReceiverPhone("0771234567");
        parcel.setPickupAddress("Pickup address");
        parcel.setDeliveryAddress("Delivery address");
        parcel.setWeight(new BigDecimal("1.250"));
        parcel.setSize("SMALL");
        parcel.setParcelType("STANDARD");
        parcel.setCharge(new BigDecimal("500.00"));
        UUID id = parcelService.createParcel(parcel).getId();

        parcelService.changeStatus(id, ParcelStatus.PICKED_UP, actor, "Courier collected parcel");
        parcelService.changeStatus(id, ParcelStatus.IN_TRANSIT, actor, "Parcel in transit");
        parcelService.changeStatus(id, ParcelStatus.OUT_FOR_DELIVERY, actor, "Courier delivering");
        parcelService.changeStatus(id, ParcelStatus.DELIVERED, actor, "Delivery completed");

        assertThat(parcels.findById(id).orElseThrow().getStatus()).isEqualTo(ParcelStatus.DELIVERED);
        var entries = history.findByParcelIdOrderByChangedAtAsc(id);
        assertThat(entries).extracting(entry -> entry.getPreviousStatus())
                .containsExactly(null, ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
                        ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY);
        assertThat(entries).extracting(entry -> entry.getNewStatus())
                .containsExactly(ParcelStatus.CREATED, ParcelStatus.PICKED_UP,
                        ParcelStatus.IN_TRANSIT, ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.DELIVERED);
        assertThat(entries).extracting(entry -> entry.getNote())
                .containsExactly("Parcel created", "Courier collected parcel", "Parcel in transit",
                        "Courier delivering", "Delivery completed");
    }

    @Test
    void contextLoadsAndReportsHealthy() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }
}
