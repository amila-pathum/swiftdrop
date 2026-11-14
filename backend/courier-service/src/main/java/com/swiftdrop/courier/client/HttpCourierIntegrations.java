package com.swiftdrop.courier.client;

import com.swiftdrop.courier.client.CourierIntegrations.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

@Configuration
public class HttpCourierIntegrations {
    private RestClient client(String base) {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }
    private <T> T call(String service, Supplier<T> request) {
        try {
            T result=request.get();
            if(result==null) throw new RemoteFailure(service,false);
            return result;
        } catch(RestClientResponseException e) {
            boolean rejected=e.getStatusCode().is4xxClientError() && e.getStatusCode().value()!=429;
            throw new RemoteFailure(service,rejected);
        } catch(RestClientException e) { throw new RemoteFailure(service,false); }
    }
    @Bean IdentityClient identityClient(@Value("${swiftdrop.clients.identity-url:http://localhost:8081}") String url) {
        var http=client(url);
        return (userId,token)->call("Identity service",()->http.get().uri("/api/admin/users/{id}",userId)
            .headers(h->h.setBearerAuth(token)).retrieve().body(IdentityAccount.class));
    }
    @Bean DispatchClient dispatchClient(@Value("${swiftdrop.clients.dispatch-url:http://localhost:8085}") String url) {
        var http=client(url);
        return (courierId,token)->call("Dispatch service",()->http.get().uri("/api/dispatch/couriers/{id}/assignments",courierId)
            .headers(h->h.setBearerAuth(token)).retrieve().body(new ParameterizedTypeReference<List<Assignment>>(){}));
    }
    @Bean ParcelClient parcelClient(@Value("${swiftdrop.clients.parcel-url:http://localhost:8082}") String url) {
        var http=client(url);
        return new ParcelClient() {
            public ParcelDetails parcel(UUID id,String token) {
                return call("Parcel service",()->http.get().uri("/api/parcels/{id}",id)
                    .headers(h->h.setBearerAuth(token)).retrieve().body(ParcelDetails.class));
            }
            public void update(UUID id,StatusUpdate update,String token) {
                call("Parcel service",()->{
                    var response=http.post().uri("/api/parcels/{id}/status",id)
                        .headers(h->{h.setBearerAuth(token);h.set("Idempotency-Key",update.submissionId().toString());})
                        .body(update).retrieve().toBodilessEntity();
                    if(response.getStatusCode().value()==202)throw new RemoteFailure("Parcel service",false);
                    return Boolean.TRUE;
                });
            }
        };
    }
}
