package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.CourierProfile;
import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.exception.DispatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "swiftdrop.dispatch.data-source", havingValue = "http")
public class HttpDispatchDataClient implements DispatchDataClient {
    private final RestClient parcels;
    private final RestClient couriers;
    private final String createdParcelsPath;
    private final String parcelPath;
    private final String couriersPath;
    private final String courierPath;

    public HttpDispatchDataClient(RestClient.Builder builder,
            @Value("${swiftdrop.parcel.base-url:http://localhost:8082}") String parcelBaseUrl,
            @Value("${swiftdrop.courier.base-url:http://localhost:8084}") String courierBaseUrl,
            @Value("${swiftdrop.parcel.created-path:/api/parcels?status=CREATED}") String createdParcelsPath,
            @Value("${swiftdrop.parcel.path:/api/parcels/{parcelId}}") String parcelPath,
            @Value("${swiftdrop.courier.list-path:/api/couriers}") String couriersPath,
            @Value("${swiftdrop.courier.path:/api/couriers/{courierId}}") String courierPath) {
        this.parcels = buildClient(builder, parcelBaseUrl);
        this.couriers = buildClient(builder, courierBaseUrl);
        this.createdParcelsPath = createdParcelsPath;
        this.parcelPath = parcelPath;
        this.couriersPath = couriersPath;
        this.courierPath = courierPath;
    }

    @Override
    public List<ParcelRow> createdParcels() {
        try {
            List<ParcelRow> response = parcels.get().uri(createdParcelsPath)
                .retrieve().body(new ParameterizedTypeReference<>() { });
            return requireResponse(response, "Parcel service returned no parcel list");
        } catch (RestClientException exception) {
            throw unavailable("Parcel service is unavailable", exception);
        }
    }

    @Override
    public Optional<ParcelRow> findParcel(UUID parcelId) {
        try {
            ParcelRow response = parcels.get().uri(parcelPath, parcelId)
                .retrieve().body(ParcelRow.class);
            if (response == null) throw new DispatchException(
                HttpStatus.BAD_GATEWAY, "Parcel service returned no parcel");
            return Optional.of(response);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw unavailable("Parcel service is unavailable", exception);
        }
    }

    @Override
    public List<CourierProfile> couriers() {
        try {
            List<CourierProfile> response = couriers.get().uri(couriersPath)
                .retrieve().body(new ParameterizedTypeReference<>() { });
            return requireResponse(response, "Courier service returned no courier list");
        } catch (RestClientException exception) {
            throw unavailable("Courier service is unavailable", exception);
        }
    }

    @Override
    public Optional<CourierProfile> findCourier(UUID courierId) {
        try {
            CourierProfile response = couriers.get().uri(courierPath, courierId)
                .retrieve().body(CourierProfile.class);
            if (response == null) throw new DispatchException(
                HttpStatus.BAD_GATEWAY, "Courier service returned no courier");
            return Optional.of(response);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw unavailable("Courier service is unavailable", exception);
        }
    }

    private static <T> List<T> requireResponse(List<T> response, String message) {
        if (response == null) throw new DispatchException(HttpStatus.BAD_GATEWAY, message);
        return response;
    }

    private static DispatchException unavailable(String message, RestClientException cause) {
        return new DispatchException(HttpStatus.SERVICE_UNAVAILABLE, message, cause);
    }

    private static RestClient buildClient(RestClient.Builder builder, String baseUrl) {
        return builder.clone().baseUrl(baseUrl).requestInterceptor((request, body, execution) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt) {
                request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
            }
            return execution.execute(request, body);
        }).build();
    }
}
