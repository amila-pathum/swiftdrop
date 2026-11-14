package com.swiftdrop.courier.client;
import com.swiftdrop.courier.client.CourierIntegrations.*;
import com.swiftdrop.courier.entity.DeliveryStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class HttpCourierIntegrationsTest {
    HttpServer server;String url;AtomicReference<String> authorization,body,idempotency;
    @BeforeEach void setup() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);url="http://127.0.0.1:"+server.getAddress().getPort();
        authorization=new AtomicReference<>();body=new AtomicReference<>();idempotency=new AtomicReference<>();
    }
    void reply(String json,int status) {
        server.createContext("/",exchange->{
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] bytes=json.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void identityAdapterReadsNumericAccountAndForwardsToken() {
        reply("{\"id\":42,\"role\":\"COURIER\",\"status\":\"ACTIVE\",\"extra\":\"ignored\"}",200);
        assertThat(new HttpCourierIntegrations().identityClient(url).account(42L,"test-token").id()).isEqualTo(42L);
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
    }
    @Test void parcelUpdateForwardsAssignmentAndStableIdempotencyKey() {
        reply("{}",200);UUID submission=UUID.randomUUID(),assignment=UUID.randomUUID();
        new HttpCourierIntegrations().parcelClient(url).update(UUID.randomUUID(),new StatusUpdate(DeliveryStatus.PICKED_UP,"Collected",42L,10L,assignment,2,1,submission),"test-token");
        assertThat(idempotency.get()).isEqualTo(submission.toString());assertThat(body.get()).contains("\"actorId\":42","\"assignmentVersion\":2",assignment.toString());
    }
    @Test void remoteAcceptanceIsNotTreatedAsConfirmation() {
        reply("{}",202);
        assertThatThrownBy(()->new HttpCourierIntegrations().parcelClient(url).update(UUID.randomUUID(),new StatusUpdate(DeliveryStatus.DELIVERED,null,42L,10L,UUID.randomUUID(),0,1,UUID.randomUUID()),"test-token"))
            .isInstanceOfSatisfying(RemoteFailure.class,e->assertThat(e.rejected).isFalse());
    }
    @Test void unavailableDependenciesFailClosed() {
        reply("{}",503);
        assertThatThrownBy(()->new HttpCourierIntegrations().dispatchClient(url).assignments(10L,"test-token"))
            .isInstanceOfSatisfying(RemoteFailure.class,e->assertThat(e.rejected).isFalse());
    }
}
