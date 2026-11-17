package com.stylecart.service;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ShipmentService {
    private final SettingsService settings;private final BookingTransactions transactions;private final OrderService orders;
    private final RestClient http;
    public ShipmentService(SettingsService settings,BookingTransactions transactions,OrderService orders){
        this.settings=settings;this.transactions=transactions;this.orders=orders;
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(18));http=RestClient.builder().requestFactory(factory).build();
    }
    public OrderService.OrderView book(UUID id,boolean verifyDuplicate){
        var credentials=settings.credentials();if(credentials.apiKey()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Save a SwiftDrop merchant API key in Integration first.");
        var job=transactions.prepare(id,verifyDuplicate);if(job.payload()==null)return orders.get(id,null);
        try{
            var response=http.post().uri(credentials.gateway()+"/api/merchants/integration/orders")
                .header("X-API-Key",credentials.apiKey()).contentType(MediaType.APPLICATION_JSON).body(job.payload()).retrieve().toEntity(Reply.class);
            var reply=response.getBody();
            if(!response.getStatusCode().is2xxSuccessful()||reply==null||!job.reference().equals(reply.merchantOrderReference())
                ||reply.trackingNumber()==null||reply.trackingNumber().isBlank()||reply.trackingNumber().length()>100||reply.status()==null||!OrderService.STATUSES.contains(reply.status()))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"SwiftDrop did not return a valid shipment confirmation.");
            transactions.complete(id,reply);return orders.get(id,null);
        }catch(RestClientResponseException e){
            int status=e.getStatusCode().value();String message=switch(status){
                case 400->"SwiftDrop rejected the delivery details. Check the district and parcel details.";
                case 401->"SwiftDrop rejected the merchant API key. Check it in Integration.";
                case 403->"SwiftDrop requires an approved merchant and an active API key.";
                case 409->"SwiftDrop reports a reference conflict. Keep this original order reference.";
                default->"SwiftDrop is unavailable. Retry this same booking without creating a new order.";};
            transactions.failed(id,message);throw new ResponseStatusException(HttpStatusCode.valueOf(status==400||status==401||status==403||status==409?status:503),message);
        }catch(ResponseStatusException e){transactions.failed(id,e.getReason());throw e;}
        catch(RuntimeException e){String message="No reliable confirmation from SwiftDrop. Retry this original booking; its reference and request are preserved.";transactions.failed(id,message);throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,message);}
    }
    public void check(){var c=settings.credentials();if(c.apiKey()==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Configure an API key first.");
        try{var r=http.get().uri(c.gateway()+"/api/merchants/integration/orders?page=0&size=1").header("X-API-Key",c.apiKey()).retrieve().toBodilessEntity();
            if(!r.getStatusCode().is2xxSuccessful())throw new IllegalStateException();
        }catch(RuntimeException e){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Connection check failed. Verify SwiftDrop is running, merchant approval and the API key.");}}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Reply(String merchantOrderReference,String trackingNumber,String status,Parcel parcel){}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Parcel(BigDecimal charge){}
}
