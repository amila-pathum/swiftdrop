package com.swiftdrop.parcel;

import com.swiftdrop.parcel.client.PricingClient;
import com.swiftdrop.parcel.merchantintegration.*;
import com.swiftdrop.parcel.repository.ParcelRepository;
import java.math.BigDecimal;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"PARCEL_DB_PASSWORD=test", "swiftdrop.events.publisher-enabled=false",
        "swiftdrop.jwt.secret=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="})
@AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class MerchantOrderIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ParcelRepository parcels;
    @Autowired ObjectMapper mapper;
    @Autowired MerchantIntegrationService service;
    @MockitoBean PricingClient pricing;
    static final String PATH="/api/parcels/merchant/integration/orders";
    static final String BODY="""
        {"merchantOrderReference":"SHOP-100","sourceRequestHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
         "parcel":{"receiverName":"Demo Receiver","receiverPhone":"0771234567",
         "pickupAddressLine1":"10 Main Street","pickupCity":"Colombo","deliveryAddressLine1":"20 Lake Road",
         "deliveryCity":"Kandy","weight":1.250,"size":"MEDIUM","parcelType":"STANDARD"}}
        """;
    @BeforeEach void setup() {
        jdbc.execute("TRUNCATE merchant_order_idempotency,parcel_event_outbox,parcel_status_history,parcels CASCADE");
        when(pricing.quote(any(),anyString())).thenReturn(new BigDecimal("450.00"));
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor merchant(String user) {
        return jwt().jwt(j -> j.subject(user).claim("purpose","MERCHANT_ORDER_INTEGRATION")
                .claim("aud",java.util.List.of("parcel-service","pricing-service")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }
    private String create(String user,String body) throws Exception {
        return mvc.perform(post(PATH).with(merchant(user)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    @Test void duplicateReturnsOriginalAndCreationEventContainsTheReference() throws Exception {
        var first=mapper.readTree(create("42",BODY)); var second=mapper.readTree(create("42",BODY));
        assertEquals(first.get("parcelId"),second.get("parcelId")); assertEquals(first.get("trackingNumber"),second.get("trackingNumber"));
        assertEquals(1,parcels.count()); verify(pricing,times(1)).quote(any(),anyString());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM parcel_event_outbox",Integer.class));
        var payload=mapper.readTree(jdbc.queryForObject("SELECT payload FROM parcel_event_outbox",String.class));
        assertEquals("42",payload.get("merchantId").asText()); assertEquals("SHOP-100",payload.get("merchantOrderReference").asText());
        assertEquals("CREATED",payload.get("newStatus").asText()); assertTrue(payload.get("oldStatus").isNull());
    }
    @Test void anotherMerchantCannotReadTheOrderButCanReuseItsReferenceForTheirOwnParcel() throws Exception {
        var first=mapper.readTree(create("42",BODY));
        mvc.perform(get(PATH+"/by-reference").param("reference","SHOP-100").with(merchant("99"))).andExpect(status().isNotFound());
        mvc.perform(get(PATH).with(merchant("99"))).andExpect(jsonPath("$.total").value(0));
        var second=mapper.readTree(create("99",BODY));
        assertNotEquals(first.get("parcelId"),second.get("parcelId")); assertEquals(2,parcels.count());
    }
    @Test void changedDetailsCannotReuseAReference() throws Exception {
        create("42",BODY);
        mvc.perform(post(PATH).with(merchant("42")).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("1.250","2.500"))).andExpect(status().isConflict());
        assertEquals(1,parcels.count());
    }
    @Test void concurrentRetriesCreateOneParcel() throws Exception {
        var request=mapper.readValue(BODY,MerchantIntegrationController.Request.class);
        try(var pool=Executors.newFixedThreadPool(4)) {
            var jobs=java.util.stream.IntStream.range(0,4).mapToObj(i -> (Callable<MerchantIntegrationController.Order>)
                    () -> service.create(42L,"test-token",request)).toList();
            var results=pool.invokeAll(jobs); var first=results.getFirst().get().parcelId();
            for(var result:results) assertEquals(first,result.get().parcelId());
        }
        assertEquals(1,parcels.count()); verify(pricing,times(1)).quote(any(),anyString());
    }
    @Test void pricingFailureRollsBackReservationAndRetryCanSucceed() throws Exception {
        when(pricing.quote(any(),anyString())).thenThrow(new IllegalStateException("test failure"));
        mvc.perform(post(PATH).with(merchant("42")).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isServiceUnavailable());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM merchant_order_idempotency",Integer.class));
        doReturn(new BigDecimal("450.00")).when(pricing).quote(any(),anyString());
        create("42",BODY); assertEquals(1,parcels.count());
    }
    @Test void onlyAMerchantCanCreateOrders() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).with(jwt().jwt(j -> j.subject("42")).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        assertEquals(0,parcels.count());
    }

    @Test void ordinaryLoginJwtCannotBypassTheMerchantApiKeyApprovalChecks() throws Exception {
        mvc.perform(post(PATH).with(jwt().jwt(j -> j.subject("42"))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        assertEquals(0,parcels.count());
    }
}
