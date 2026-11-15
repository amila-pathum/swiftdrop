package com.swiftdrop.dispatch.client;

import com.swiftdrop.dispatch.dto.response.DispatchResponses.ParcelRow;
import com.swiftdrop.dispatch.exception.DispatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Calls parcel-service and courier-service with the caller's own access token,
 * so their role checks apply to the dispatcher or admin making the request.
 */
@Component
public class HttpDispatchDataClient implements DispatchDataClient {
    static final int COURIER_PAGE_SIZE = 100;
    /** Guards against a misbehaving upstream that never reports the last page. */
    static final int MAX_COURIER_PAGES = 50;

    private final RestClient parcels;
    private final RestClient couriers;
    private final String createdParcelsPath;
    private final String parcelPath;
    private final String assignCourierPath;
    private final String courierProfilesPath;

    public HttpDispatchDataClient(RestClient.Builder builder,
            @Value("${swiftdrop.parcel.base-url:http://localhost:8082}") String parcelBaseUrl,
            @Value("${swiftdrop.courier.base-url:http://localhost:8084}") String courierBaseUrl,
            @Value("${swiftdrop.parcel.created-path:/api/parcels/dispatch?status=CREATED}") String createdParcelsPath,
            @Value("${swiftdrop.parcel.path:/api/parcels/dispatch/{parcelId}}") String parcelPath,
            @Value("${swiftdrop.parcel.assign-courier-path:/api/parcels/{parcelId}/courier}") String assignCourierPath,
            @Value("${swiftdrop.courier.profiles-path:/api/couriers/profiles?active=true&page={page}&size={size}}")
                String courierProfilesPath) {
        this.parcels = buildClient(builder, parcelBaseUrl);
        this.couriers = buildClient(builder, courierBaseUrl);
        this.createdParcelsPath = createdParcelsPath;
        this.parcelPath = parcelPath;
        this.assignCourierPath = assignCourierPath;
        this.courierProfilesPath = courierProfilesPath;
    }

    @Override
    public List<ParcelRow> createdParcels() {
        try {
            List<ParcelRow> response = parcels.get().uri(createdParcelsPath)
                .retrieve().body(new ParameterizedTypeReference<List<ParcelRow>>() { });
            if (response == null) throw badGateway("Parcel service returned no parcel list");
            return response.stream().filter(Objects::nonNull).toList();
        } catch (RestClientException exception) {
            throw unavailable("Parcel service is unavailable", exception);
        }
    }

    @Override
    public Optional<ParcelRow> findParcel(UUID parcelId) {
        try {
            ParcelRow response = parcels.get().uri(parcelPath, parcelId)
                .retrieve().body(ParcelRow.class);
            if (response == null) throw badGateway("Parcel service returned no parcel");
            return Optional.of(response);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw unavailable("Parcel service is unavailable", exception);
        }
    }

    @Override
    public List<CourierData> couriers() {
        List<CourierData> all = new ArrayList<>();
        try {
            for (int page = 0; page < MAX_COURIER_PAGES; page++) {
                CourierPage response = couriers.get().uri(courierProfilesPath, page, COURIER_PAGE_SIZE)
                    .retrieve().body(CourierPage.class);
                if (response == null || response.content() == null) {
                    throw badGateway("Courier service returned no courier list");
                }
                response.content().stream().filter(Objects::nonNull).forEach(all::add);
                if (page + 1 >= response.totalPages()) break;
            }
        } catch (RestClientException exception) {
            throw unavailable("Courier service is unavailable", exception);
        }
        return all;
    }

    @Override
    public Optional<CourierData> findCourier(Long courierProfileId) {
        // TODO(R3): use GET /api/couriers/profiles/{id} once courier-service provides it.
        return couriers().stream().filter(courier -> courierProfileId.equals(courier.id())).findFirst();
    }

    @Override
    public void assignParcelCourier(UUID parcelId, Long courierUserId) {
        try {
            parcels.patch().uri(assignCourierPath, parcelId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("courierUserId", courierUserId))
                .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new DispatchException(HttpStatus.NOT_FOUND, "Parcel not found", exception);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new DispatchException(HttpStatus.CONFLICT,
                "Parcel service refused the courier assignment", exception);
        } catch (HttpClientErrorException exception) {
            throw new DispatchException(HttpStatus.BAD_GATEWAY,
                "Parcel service rejected the courier assignment", exception);
        } catch (RestClientException exception) {
            throw unavailable("Parcel service is unavailable", exception);
        }
    }

    /** courier-service PageResult of Profile; unknown profile fields are ignored. */
    record CourierPage(List<CourierData> content, int page, int size, long totalElements, int totalPages) { }

    private static DispatchException badGateway(String message) {
        return new DispatchException(HttpStatus.BAD_GATEWAY, message);
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
