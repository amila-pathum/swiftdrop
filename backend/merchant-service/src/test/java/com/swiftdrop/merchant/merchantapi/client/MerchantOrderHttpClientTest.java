package com.swiftdrop.merchant.merchantapi.client;

import com.sun.net.httpserver.HttpServer;
import com.swiftdrop.merchant.entity.*;
import com.swiftdrop.merchant.repository.MerchantRepository;
import com.swiftdrop.merchant.merchantapi.dto.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MerchantOrderHttpClientTest {
    static final String SECRET="AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    static final String SERVICE="test-merchant-service-token-32-characters";
    final ObjectMapper mapper=new ObjectMapper();
    HttpServer server;
    HttpParcelOrderClient client;
    IdentityMerchantTokenClient tokens;
    Merchant merchant;
    int parcelStatus,identityStatus,parcelCalls;
    String subject,purpose,requestedUser,authorization,serviceToken;
    tools.jackson.databind.JsonNode parcelRequest;
    boolean malformed;
    @BeforeEach void start() throws Exception {
        parcelStatus=200; identityStatus=200; subject="42"; purpose="MERCHANT_ORDER_INTEGRATION";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/internal/merchant-order-tokens",exchange -> {
            requestedUser=mapper.readTree(exchange.getRequestBody().readAllBytes()).get("userId").asText();
            serviceToken=exchange.getRequestHeaders().getFirst("X-SwiftDrop-Merchant-Token");
            var now=Instant.now();
            String jwt=Jwts.builder().subject(subject).claim("role","MERCHANT").claim("status","ACTIVE").claim("purpose",purpose)
                    .audience().add("parcel-service").add("pricing-service").and()
                    .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                    .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();
            byte[] body=mapper.writeValueAsBytes(Map.of("userId",42,"accessToken",jwt,"expiresIn",60));
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(identityStatus,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.createContext("/api/parcels/merchant/integration/orders",exchange -> {
            parcelCalls++; authorization=exchange.getRequestHeaders().getFirst("Authorization");
            if("POST".equals(exchange.getRequestMethod())) parcelRequest=mapper.readTree(exchange.getRequestBody().readAllBytes());
            var order=new LinkedHashMap<String,Object>();
            order.put("merchantOrderReference","SHOP-100");order.put("trackingNumber","SD-100");order.put("status","IN_TRANSIT");
            order.put("parcelId","11111111-1111-4111-8111-111111111111");order.put("charge",450);
            order.put("createdAt","2026-11-15T00:00:00Z");order.put("weightKg",1.25);order.put("size","MEDIUM");order.put("serviceType","STANDARD");
            Object result=exchange.getRequestURI().getQuery()!=null && exchange.getRequestURI().getQuery().startsWith("page=")
                    ? Map.of("items",List.of(order),"total",1,"page",0,"size",20):order;
            byte[] body=mapper.writeValueAsBytes(malformed?Map.of("error","sensitive upstream detail"):result);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(parcelStatus,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}
        });
        server.start(); String base="http://127.0.0.1:"+server.getAddress().getPort();
        var decoder=NimbusJwtDecoder.withSecretKey(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).macAlgorithm(MacAlgorithm.HS256).build();
        tokens=new IdentityMerchantTokenClient(base,SERVICE,decoder);
        var merchants=mock(MerchantRepository.class); merchant=mock(Merchant.class);
        when(merchant.getUserId()).thenReturn(42L); when(merchant.getStatus()).thenReturn(MerchantStatus.APPROVED);
        when(merchants.findById(7L)).thenReturn(Optional.of(merchant));
        client=new HttpParcelOrderClient(base,merchants,tokens,mapper);
    }
    @AfterEach void stop(){server.stop(0);}
    private CreateMerchantOrderRequest request() {
        var contact=new DeliveryContactRequest("Demo","0771234567","Test address","Colombo",null);
        return new CreateMerchantOrderRequest("SHOP-100",contact,contact,new ParcelDetailsRequest("Clothing",new java.math.BigDecimal("1.25"),
                java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,java.math.BigDecimal.TEN,null));
    }
    @Test void realHttpDelegatesTheUserIdRatherThanProfileIdAndMapsTheParcelContract() {
        var result=client.createOrder("7",request());
        assertEquals("42",requestedUser);assertEquals(SERVICE,serviceToken);assertTrue(authorization.startsWith("Bearer "));
        assertEquals("SD-100",result.trackingNumber());assertEquals("IN_TRANSIT",result.status());assertNotNull(result.parcel());
        assertEquals("Colombo",parcelRequest.get("parcel").get("pickupCity").asText());
        assertEquals("MEDIUM",parcelRequest.get("parcel").get("size").asText());
        assertEquals(64,parcelRequest.get("sourceRequestHash").asText().length());
        assertTrue(client.idempotentCreation());
    }
    @Test void listAndDetailUseTheAuthenticatedParcelEndpointsForCurrentStatus() {
        assertEquals("IN_TRANSIT",client.current("7","SHOP-100").orElseThrow().status());
        assertEquals(1,client.list("7",0,20).orElseThrow().total());
    }
    @Test void unapprovedProfileCannotRequestADelegatedIdentityToken() {
        when(merchant.getStatus()).thenReturn(MerchantStatus.PENDING);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->client.createOrder("7",request())).getStatusCode().value());
        assertNull(requestedUser);assertEquals(0,parcelCalls);
    }
    @Test void wrongSubjectOrPurposeFromIdentityIsNeverForwardedToParcel() {
        subject="99"; assertThrows(ResponseStatusException.class,()->tokens.forUser(42));
        subject="42";purpose="wrong";assertThrows(ResponseStatusException.class,()->tokens.forUser(42));
        assertEquals(0,parcelCalls);
    }
    @Test void dependencyErrorsDoNotExposeBodiesAndMalformedSuccessIsRejected() {
        identityStatus=403;
        assertEquals(403,assertThrows(ResponseStatusException.class,()->client.createOrder("7",request())).getStatusCode().value());
        identityStatus=200;malformed=true;
        var failure=assertThrows(ResponseStatusException.class,()->client.createOrder("7",request()));
        assertEquals(503,failure.getStatusCode().value());assertFalse(failure.getMessage().contains("sensitive upstream"));
        parcelStatus=409;
        assertEquals(409,assertThrows(ResponseStatusException.class,()->client.createOrder("7",request())).getStatusCode().value());
    }
}
