package com.stylecart;

import com.stylecart.model.*;
import com.stylecart.repo.*;
import com.stylecart.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring Security/JPA/PostgreSQL; the external SwiftDrop HTTP server is a test double. */
@SpringBootTest(properties={"spring.config.import=","server.ssl.enabled=false","stylecart.admin.password=test-admin-password-123",
    "stylecart.settings-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=","stylecart.api-key=test-merchant-api-key","stylecart.webhook-secret=test-signing-secret"})
@AutoConfigureMockMvc @Testcontainers(disabledWithoutDocker=true)
class StyleCartIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18");
    static final ObjectMapper JSON=new ObjectMapper();
    static final HttpServer gateway;
    static final List<String> requests=new CopyOnWriteArrayList<>();
    static volatile int upstream=200;
    static volatile String receivedKey;
    static {
        try{gateway=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);gateway.createContext("/api/merchants/integration/orders",exchange->{
            String raw=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);receivedKey=exchange.getRequestHeaders().getFirst("X-API-Key");
            byte[] body;
            if(exchange.getRequestMethod().equals("POST")){
                requests.add(raw);String reference=JSON.readTree(raw).get("merchantOrderReference").asText();
                body=JSON.writeValueAsBytes(Map.of("merchantOrderReference",reference,"trackingNumber",tracking(reference),"status","CREATED","parcel",Map.of("charge",450)));
            }else body="{\"items\":[],\"total\":0,\"page\":0,\"size\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(upstream,body.length);try(var out=exchange.getResponseBody()){out.write(body);}
        });gateway.start();}catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){String base="http://127.0.0.1:"+gateway.getAddress().getPort();r.add("stylecart.gateway",()->base);r.add("stylecart.allowed-gateways",()->base);}
    @AfterAll static void stop(){gateway.stop(0);}
    @Autowired MockMvc mvc;@Autowired UserRepository users;@Autowired OrderRepository orders;@Autowired SettingsRepository settings;
    @Autowired EventRepository events;@Autowired AttemptRepository attempts;@Autowired PasswordEncoder passwords;@Autowired SettingsService integration;
    @Autowired JdbcTemplate jdbc;@Autowired SecretBox secretBox;@Autowired LiveUpdates live;
    ShopUser buyer,other;
    static String tracking(String ref){return "SD"+Integer.toUnsignedString(ref.hashCode());}
    @BeforeEach void setup(){
        jdbc.execute("TRUNCATE webhook_attempts,webhook_events,store_order_items,store_orders CASCADE");requests.clear();upstream=200;receivedKey=null;
        buyer=customer();other=customer();var config=settings.findById(1L).orElseThrow();config.failureMode="NORMAL";config.failuresRemaining=0;settings.saveAndFlush(config);
        var view=integration.view();integration.update(new SettingsService.Update(view.gatewayUrl(),"test-merchant-api-key","test-signing-secret","Demo shop","0112345678","10 Main Street","Colombo","00100"));
    }
    ShopUser customer(){var u=new ShopUser();u.name="Demo Buyer";u.email=UUID.randomUUID()+"@example.test";u.role="CUSTOMER";u.passwordHash=passwords.encode("test-buyer-password");return users.saveAndFlush(u);}
    String checkout(ShopUser who) throws Exception {return mvc.perform(post("/api/customer/orders").with(user(who.email).roles("CUSTOMER")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
        {"items":[{"productId":"tee","size":"M","color":"White","quantity":2,"price":1}],"name":"Demo Receiver","phone":"0771234567","address":"20 Lake Road","city":"Kandy","postalCode":"20000"}
        """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();}
    MvcResult book(UUID id,boolean verify,int expected) throws Exception{return mvc.perform(post("/api/admin/orders/"+id+"/shipment").with(user("admin@stylecart.local").roles("ADMIN")).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"verifyDuplicate\":"+verify+"}"))
        .andExpect(status().is(expected)).andReturn();}
    Map<String,Object> event(String reference,String status,Instant when){var value=new LinkedHashMap<String,Object>();value.put("eventId",UUID.randomUUID().toString());value.put("eventType","ParcelStatusChanged");value.put("occurredAt",when.toString());value.put("trackingNumber",tracking(reference));value.put("merchantOrderReference",reference);value.put("newStatus",status);return value;}
    MvcResult webhook(Map<String,Object> value,String secret,long timestamp,int expected) throws Exception{
        byte[] body=JSON.writeValueAsBytes(value);var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));String signature="sha256="+HexFormat.of().formatHex(mac.doFinal(body));
        return mvc.perform(post("/webhooks/swiftdrop").contentType(MediaType.APPLICATION_JSON).content(body).header("X-SwiftDrop-Timestamp",timestamp).header("X-SwiftDrop-Signature",signature).header("X-SwiftDrop-Event-Id",value.get("eventId"))).andExpect(status().is(expected)).andReturn();
    }
    void webhook(Map<String,Object> value,int expected) throws Exception{webhook(value,"test-signing-secret",Instant.now().getEpochSecond(),expected);}
    @Test void catalogAndAuthoritativePricesWorkWithoutTrustingTheBrowser() throws Exception{
        mvc.perform(get("/api/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(6));
        var order=JSON.readTree(checkout(buyer));assertEquals(5800,order.get("subtotal").asInt());assertTrue(order.get("trackingNumber").isNull());assertEquals("NOT_BOOKED",order.get("shipmentState").asText());
    }
    @Test void loginRestoresASessionAndRegistrationCannotChooseAdminRole() throws Exception{
        String email=UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(Map.of("name","New Buyer","email",email,"password","new-password-123","role","ADMIN"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER")).andExpect(jsonPath("$.passwordHash").doesNotExist());
        var login=mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsBytes(Map.of("email",email,"password","new-password-123")))).andExpect(status().isOk()).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        assertNotEquals("new-password-123",users.findByEmailIgnoreCase(email).orElseThrow().passwordHash);
    }
    @Test void authCsrfAndOrderOwnershipAreEnforced() throws Exception{
        var order=JSON.readTree(checkout(buyer));String id=order.get("id").asText();
        mvc.perform(get("/api/customer/orders/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/customer/orders/"+id).with(user(other.email).roles("CUSTOMER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/customer/orders").with(user(other.email).roles("CUSTOMER"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/orders").with(user(buyer.email).roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/customer/orders").with(user(buyer.email).roles("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @Test void shipmentUsesRealApiContractAndStableReferenceWithServerSideApiKey() throws Exception{
        var order=JSON.readTree(checkout(buyer));UUID id=UUID.fromString(order.get("id").asText());String reference=order.get("reference").asText();
        var result=book(id,false,200);assertEquals(tracking(reference),JSON.readTree(result.getResponse().getContentAsString()).get("trackingNumber").asText());
        book(id,false,200);assertEquals(1,requests.size());book(id,true,200);assertEquals(2,requests.size());assertEquals(requests.get(0),requests.get(1));
        assertEquals("test-merchant-api-key",receivedKey);var payload=JSON.readTree(requests.getFirst());assertEquals(reference,payload.get("merchantOrderReference").asText());assertEquals("Kandy",payload.get("receiver").get("city").asText());assertEquals("MEDIUM",payload.get("parcel").get("size").asText());
    }
    @Test void outageNeverCreatesFakeTrackingAndRetryKeepsOriginalPayload() throws Exception{
        var order=JSON.readTree(checkout(buyer));UUID id=UUID.fromString(order.get("id").asText());upstream=503;book(id,false,503);
        var saved=orders.findById(id).orElseThrow();assertNull(saved.trackingNumber);assertEquals("NEEDS_REVIEW",saved.shipmentState);
        upstream=200;book(id,false,200);assertEquals(requests.get(0),requests.get(1));assertEquals(1,orders.count());
    }
    @Test void signedWebhookBeforeShipmentResponseUpdatesTheCorrectOrderAndLateEventsDoNotRegressIt() throws Exception{
        var order=JSON.readTree(checkout(buyer));UUID id=UUID.fromString(order.get("id").asText());String reference=order.get("reference").asText();var now=Instant.now();
        webhook(event(reference,"DELIVERED",now),204);book(id,true,200);webhook(event(reference,"PICKED_UP",now.minusSeconds(60)),204);
        assertEquals("DELIVERED",orders.findById(id).orElseThrow().deliveryStatus);assertEquals(2,events.findByOrderIdOrderByOccurredAtAsc(id).size());
    }
    @Test void invalidSignaturesAndTimestampsCannotUpdateOrdersOrConsumeFailureControl() throws Exception{
        String reference=JSON.readTree(checkout(buyer)).get("reference").asText();integration.failures("NEXT_TWO");var payload=event(reference,"DELIVERED",Instant.now());
        webhook(payload,"wrong-secret",Instant.now().getEpochSecond(),401);webhook(payload,"test-signing-secret",Instant.now().getEpochSecond()-301,401);
        assertEquals(0,events.count());assertEquals(2,integration.view().failuresRemaining());assertNull(orders.findByReference(reference).orElseThrow().deliveryStatus);
    }
    @Test void twoFailuresThenSuccessPersistAttemptsAndAcceptedDuplicatesDoNotConsumeNewFailures() throws Exception{
        String reference=JSON.readTree(checkout(buyer)).get("reference").asText();var payload=event(reference,"IN_TRANSIT",Instant.now());integration.failures("NEXT_TWO");
        webhook(payload,500);webhook(payload,500);webhook(payload,204);assertEquals(1,events.count());assertEquals(3,attempts.count());
        integration.failures("NEXT_TWO");webhook(payload,204);assertEquals(2,integration.view().failuresRemaining());assertEquals(1,events.count());
        payload.put("newStatus","FAILED");webhook(payload,409);assertEquals("IN_TRANSIT",orders.findByReference(reference).orElseThrow().deliveryStatus);
    }
    @Test void durableInboxMatchesAnEarlyTrackingOnlyEventAfterBooking() throws Exception{
        var order=JSON.readTree(checkout(buyer));String reference=order.get("reference").asText();var payload=event(reference,"OUT_FOR_DELIVERY",Instant.now());payload.remove("merchantOrderReference");webhook(payload,204);
        assertNull(events.findAll().getFirst().orderId);book(UUID.fromString(order.get("id").asText()),false,200);assertEquals("OUT_FOR_DELIVERY",orders.findByReference(reference).orElseThrow().deliveryStatus);
    }
    @Test void settingsAreEncryptedAndNeverReturnedAsCredentials() throws Exception{
        var s=settings.findById(1L).orElseThrow();assertNotEquals("test-merchant-api-key",s.apiKeyCipher);assertEquals("test-signing-secret",secretBox.decrypt(s.secretCipher));
        var response=mvc.perform(get("/api/admin/integration").with(user("admin@stylecart.local").roles("ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("test-merchant-api-key"));assertFalse(response.contains("test-signing-secret"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->integration.update(new SettingsService.Update("http://169.254.169.254",null,null,"","","","Colombo","")));
    }
    @Test void testWebhooksDoNotCreateOrdersAndAlwaysFailModeCanBeCleared() throws Exception{
        var payload=new HashMap<String,Object>();payload.put("eventId",UUID.randomUUID().toString());payload.put("eventType","WebhookTest");payload.put("occurredAt",Instant.now().toString());payload.put("trackingNumber","TEST");payload.put("newStatus","TEST");
        integration.failures("ALWAYS");webhook(payload,500);webhook(payload,500);integration.failures("NORMAL");webhook(payload,204);assertEquals(0,orders.count());assertEquals(1,events.count());
    }
    @Test void sseNotifiesOnlyTheOrderOwnerAndAdminsAfterCommit() throws Exception{
        var session1=new MockHttpSession();
        var session2=new MockHttpSession();
        var first=mvc.perform(get("/api/customer/live").session(session1).with(user(buyer.email).roles("CUSTOMER"))).andExpect(request().asyncStarted()).andReturn();
        var second=mvc.perform(get("/api/customer/live").session(session2).with(user(other.email).roles("CUSTOMER"))).andExpect(request().asyncStarted()).andReturn();
        checkout(buyer);
        assertTrue(first.getResponse().getContentAsString().contains("changed"));assertFalse(second.getResponse().getContentAsString().contains("changed"));
        session1.invalidate();session2.invalidate();live.heartbeat();
    }
}
