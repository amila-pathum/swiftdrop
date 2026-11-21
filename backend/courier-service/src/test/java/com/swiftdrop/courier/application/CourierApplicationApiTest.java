package com.swiftdrop.courier.application;

import com.swiftdrop.courier.client.CourierIntegrations.RemoteFailure;
import com.swiftdrop.courier.entity.VehicleType;
import com.swiftdrop.courier.exception.CourierException;
import com.swiftdrop.courier.repository.CourierProfileRepository;
import com.swiftdrop.courier.repository.DeliveryCheckpointRepository;
import com.swiftdrop.courier.repository.DeliveryProofRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Courier applications through the real HTTP API and PostgreSQL; only Identity is stubbed. */
@SpringBootTest(properties = {
    // Same as the other courier integration tests: no RabbitMQ listener is needed here.
    "spring.rabbitmq.listener.simple.auto-startup=false",
    "management.health.rabbit.enabled=false",
    "COURIER_DB_PASSWORD=unused",
    "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
class CourierApplicationApiTest {
    private static final String SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String PHONE = "+94 77 123 4599";
    private static final String BODY = """
        {"name":"Kamal Silva","phone":"%s","vehicleType":"MOTORCYCLE",
         "vehicleRegistrationNumber":"WP-BCD-1234","preferredArea":"Colombo"}
        """.formatted(PHONE);

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    @Autowired MockMvc mvc;
    @Autowired CourierApplicationRepository applications;
    @Autowired CourierProfileRepository profiles;
    @Autowired DeliveryProofRepository proofs;
    @Autowired DeliveryCheckpointRepository checkpoints;
    @MockitoBean CourierAccountActivation activation;

    @BeforeEach
    void resetData() {
        proofs.deleteAll();
        checkpoints.deleteAll();
        profiles.deleteAll();
        applications.deleteAll();
        reset(activation);
    }

    @Test
    void pendingCourierAppliesOnceAndReadsOnlyTheirOwnApplication() throws Exception {
        call(post("/api/couriers/applications"), 42, "COURIER", "PENDING_APPROVAL", BODY)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(42))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.vehicleRegistrationNumber").value("WP-BCD-1234"))
            .andExpect(jsonPath("$.submittedAt").exists());

        call(post("/api/couriers/applications"), 42, "COURIER", "PENDING_APPROVAL", BODY)
            .andExpect(status().isConflict());
        // A client-supplied userId is ignored: the applicant is always the token subject.
        call(post("/api/couriers/applications"), 43, "COURIER", "PENDING_APPROVAL",
                BODY.replace("{\"name\"", "{\"userId\":42,\"name\""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(43));

        call(get("/api/couriers/me/application"), 42, "COURIER", "PENDING_APPROVAL", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(42));
        call(get("/api/couriers/me/application"), 44, "COURIER", "PENDING_APPROVAL", null)
            .andExpect(status().isNotFound());
        // Applying does not create a usable courier profile.
        assertThat(profiles.findByUserId(42L)).isEmpty();
    }

    @Test
    void applicationInputIsValidated() throws Exception {
        for (String invalid : new String[] {
            BODY.replace("MOTORCYCLE", "ROCKET"),
            BODY.replace(PHONE, "call me"),
            BODY.replace("WP-BCD-1234", " "),
            BODY.replace("Colombo", ""),
            "{}"}) {
            call(post("/api/couriers/applications"), 42, "COURIER", "PENDING_APPROVAL", invalid)
                .andExpect(status().isBadRequest());
        }
        assertThat(applications.count()).isZero();
    }

    @Test
    void onlyCouriersApplyAndOnlyAdministratorsReview() throws Exception {
        long id = apply(42);

        mvc.perform(post("/api/couriers/applications").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
        for (String role : new String[] {"CUSTOMER", "MERCHANT", "DISPATCHER", "ADMIN"}) {
            call(post("/api/couriers/applications"), 50, role, "ACTIVE", BODY)
                .andExpect(status().isForbidden());
        }
        call(post("/api/couriers/applications"), 51, "MERCHANT", "PENDING_APPROVAL", BODY)
            .andExpect(status().isForbidden());

        String[][] others = {
            {"CUSTOMER", "ACTIVE"}, {"MERCHANT", "ACTIVE"}, {"DISPATCHER", "ACTIVE"},
            {"COURIER", "ACTIVE"}, {"COURIER", "PENDING_APPROVAL"}};
        for (String[] caller : others) {
            call(get("/api/couriers/admin/applications"), 60, caller[0], caller[1], null)
                .andExpect(status().isForbidden());
            call(get("/api/couriers/admin/applications/" + id), 60, caller[0], caller[1], null)
                .andExpect(status().isForbidden());
            call(patch("/api/couriers/admin/applications/" + id + "/approve"), 60, caller[0], caller[1], null)
                .andExpect(status().isForbidden());
            call(patch("/api/couriers/admin/applications/" + id + "/reject"), 60, caller[0], caller[1],
                    "{\"reason\":\"No\"}")
                .andExpect(status().isForbidden());
        }
        assertThat(applications.findById(id).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        verify(activation, never()).ensureActive(any(), anyString());
    }

    @Test
    void approvalActivatesTheAccountAndCreatesAnActiveProfile() throws Exception {
        long id = apply(42);

        call(patch("/api/couriers/admin/applications/" + id + "/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.reviewedAt").exists());

        verify(activation).ensureActive(eq(42L), anyString());
        var profile = profiles.findByUserId(42L).orElseThrow();
        assertThat(profile.active).isTrue();
        assertThat(profile.name).isEqualTo("Kamal Silva");
        assertThat(profile.vehicleType).isEqualTo(VehicleType.MOTORCYCLE);
        assertThat(profile.vehicleNumber).isEqualTo("WP-BCD-1234");
        assertThat(profile.area).isEqualTo("Colombo");

        // Approving again is harmless, and an approved application cannot be rejected.
        call(patch("/api/couriers/admin/applications/" + id + "/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
        call(patch("/api/couriers/admin/applications/" + id + "/reject"), 1, "ADMIN", "ACTIVE",
                "{\"reason\":\"Changed my mind\"}")
            .andExpect(status().isConflict());
        assertThat(profiles.count()).isEqualTo(1);
        verify(activation, times(2)).ensureActive(eq(42L), anyString());

        call(get("/api/couriers/me/application"), 42, "COURIER", "ACTIVE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void nothingIsApprovedWhenTheAccountCannotBeActivated() throws Exception {
        long id = apply(42);

        doThrow(new RemoteFailure("Identity service", false)).when(activation).ensureActive(eq(42L), anyString());
        call(patch("/api/couriers/admin/applications/" + id + "/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isServiceUnavailable());
        doThrow(CourierException.conflict("The login account must be a pending or active courier."))
            .when(activation).ensureActive(eq(42L), anyString());
        call(patch("/api/couriers/admin/applications/" + id + "/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isConflict());

        assertThat(applications.findById(id).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(profiles.findByUserId(42L)).isEmpty();
    }

    @Test
    void rejectionNeedsAReasonAndLeavesNoProfile() throws Exception {
        long id = apply(42);

        call(patch("/api/couriers/admin/applications/" + id + "/reject"), 1, "ADMIN", "ACTIVE", "{\"reason\":\" \"}")
            .andExpect(status().isBadRequest());
        call(patch("/api/couriers/admin/applications/" + id + "/reject"), 1, "ADMIN", "ACTIVE",
                "{\"reason\":\"Vehicle registration could not be verified\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewNote").value("Vehicle registration could not be verified"));

        // A rejected application cannot be approved or rejected again.
        call(patch("/api/couriers/admin/applications/" + id + "/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isConflict());
        call(patch("/api/couriers/admin/applications/" + id + "/reject"), 1, "ADMIN", "ACTIVE",
                "{\"reason\":\"Again\"}")
            .andExpect(status().isConflict());
        assertThat(profiles.findByUserId(42L)).isEmpty();
        verify(activation, never()).ensureActive(any(), anyString());

        // The applicant can read why.
        call(get("/api/couriers/me/application"), 42, "COURIER", "PENDING_APPROVAL", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewNote").value("Vehicle registration could not be verified"));
    }

    @Test
    void administratorListsFiltersAndViewsApplications() throws Exception {
        long first = apply(42);
        long second = apply(43);
        call(patch("/api/couriers/admin/applications/" + first + "/reject"), 1, "ADMIN", "ACTIVE",
                "{\"reason\":\"Incomplete\"}")
            .andExpect(status().isOk());

        call(get("/api/couriers/admin/applications"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
        call(get("/api/couriers/admin/applications?status=PENDING"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(second));
        call(get("/api/couriers/admin/applications?status=NOPE"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isBadRequest());
        call(get("/api/couriers/admin/applications/" + first), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));
        call(get("/api/couriers/admin/applications/999999"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isNotFound());
        call(patch("/api/couriers/admin/applications/999999/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isNotFound());
    }

    @Test
    void logsAndErrorMessagesContainNoPhoneNumbers(CapturedOutput output) throws Exception {
        long id = apply(42);
        String duplicate = call(post("/api/couriers/applications"), 42, "COURIER", "PENDING_APPROVAL", BODY)
            .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        String invalid = call(post("/api/couriers/applications"), 45, "COURIER", "PENDING_APPROVAL",
                BODY.replace("MOTORCYCLE", "ROCKET"))
            .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        call(patch("/api/couriers/admin/applications/" + id + "/approve"), 1, "ADMIN", "ACTIVE", null)
            .andExpect(status().isOk());

        assertThat(duplicate).doesNotContain(PHONE).doesNotContain("Kamal Silva");
        assertThat(invalid).doesNotContain(PHONE).doesNotContain("Kamal Silva");
        assertThat(output.getAll()).doesNotContain(PHONE).doesNotContain("77 123 4599");
    }

    private long apply(long userId) throws Exception {
        call(post("/api/couriers/applications"), userId, "COURIER", "PENDING_APPROVAL", BODY)
            .andExpect(status().isCreated());
        return applications.findByUserId(userId).orElseThrow().getId();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, long userId, String role,
            String accountStatus, String json) throws Exception {
        Instant now = Instant.now();
        String token = Jwts.builder().subject(Long.toString(userId))
            .claim("role", role).claim("status", accountStatus)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(300)))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
        request.header("Authorization", "Bearer " + token);
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(request);
    }
}
