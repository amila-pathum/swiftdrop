package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.exception.DispatchException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpDispatchDataClientTest {
    private static final String PARCEL = "http://parcel.test";
    private static final String COURIER = "http://courier.test";

    private MockRestServiceServer server;
    private HttpDispatchDataClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpDispatchDataClient(builder, PARCEL, COURIER,
            "/api/parcels/dispatch?status=CREATED", "/api/parcels/dispatch/{parcelId}",
            "/api/parcels/{parcelId}/courier", "/api/couriers/profiles?active=true&page={page}&size={size}");
        Jwt jwt = Jwt.withTokenValue("caller-token").header("alg", "HS256").subject("42")
            .claim("role", "DISPATCHER").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsCreatedParcelsFromTheDispatchEndpointWithTheCallersToken() {
        server.expect(requestTo(PARCEL + "/api/parcels/dispatch?status=CREATED"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer caller-token"))
            .andRespond(withSuccess("""
                [{"parcelId":"20000000-0000-4000-8000-000000000001","trackingNumber":"SD-1",
                  "pickupArea":"Colombo","dropoffArea":"Kandy","pickupAddress":"Colombo 03",
                  "deliveryAddress":"Kandy Town","size":"SMALL","status":"CREATED","courierId":null,
                  "courierUserId":null,"courierName":null,"charge":850.00,
                  "createdAt":"2026-11-01T08:00:00Z","updatedAt":"2026-11-01T08:00:00Z"}]
                """, MediaType.APPLICATION_JSON));

        var parcels = client.createdParcels();

        assertThat(parcels).hasSize(1);
        assertThat(parcels.get(0).trackingNumber()).isEqualTo("SD-1");
        assertThat(parcels.get(0).status()).isEqualTo("CREATED");
        server.verify();
    }

    @Test
    void unknownParcelIsEmptyAndServerErrorIsUnavailable() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(PARCEL + "/api/parcels/dispatch/" + id)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(PARCEL + "/api/parcels/dispatch/" + id)).andRespond(withServerError());

        assertThat(client.findParcel(id)).isEmpty();
        assertThatThrownBy(() -> client.findParcel(id))
            .isInstanceOf(DispatchException.class)
            .extracting("status").isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void readsEveryPageOfCourierProfiles() {
        server.expect(requestTo(COURIER + "/api/couriers/profiles?active=true&page=0&size=100"))
            .andExpect(header("Authorization", "Bearer caller-token"))
            .andRespond(withSuccess("""
                {"content":[{"id":7,"userId":70,"name":"Amal","phone":"0771234567","vehicleType":"BIKE",
                  "vehicleNumber":"AB-1","area":"Colombo","availability":"AVAILABLE","active":true,
                  "createdAt":"2026-11-01T08:00:00Z","updatedAt":"2026-11-01T08:00:00Z","version":0}],
                 "page":0,"size":100,"totalElements":2,"totalPages":2}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(COURIER + "/api/couriers/profiles?active=true&page=1&size=100"))
            .andRespond(withSuccess("""
                {"content":[{"id":9,"userId":90,"name":"Bimal","availability":"BUSY","active":true}],
                 "page":1,"size":100,"totalElements":2,"totalPages":2}
                """, MediaType.APPLICATION_JSON));

        var couriers = client.couriers();

        assertThat(couriers).extracting(DispatchDataClient.CourierData::id).containsExactly(7L, 9L);
        assertThat(couriers.get(0).available()).isTrue();
        assertThat(couriers.get(1).available()).isFalse();
        server.verify();
    }

    @Test
    void assignmentPatchesTheParcelWithTheCourierUserId() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(PARCEL + "/api/parcels/" + id + "/courier"))
            .andExpect(method(HttpMethod.PATCH))
            .andExpect(header("Authorization", "Bearer caller-token"))
            .andExpect(content().json("{\"courierUserId\":70}"))
            .andRespond(withSuccess());

        client.assignParcelCourier(id, 70L);

        server.verify();
    }

    @Test
    void assignmentRefusedByParcelServiceIsAConflict() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(PARCEL + "/api/parcels/" + id + "/courier")).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.assignParcelCourier(id, 70L))
            .isInstanceOf(DispatchException.class)
            .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }
}
