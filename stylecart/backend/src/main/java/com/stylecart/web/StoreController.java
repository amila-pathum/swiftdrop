package com.stylecart.web;
import com.stylecart.repo.*;
import com.stylecart.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController
public class StoreController {
    private final ProductRepository products;private final OrderService orders;private final AuthController auth;private final SettingsService settings;
    private final ShipmentService shipments;private final WebhookService webhook;private final AttemptRepository attempts;private final EventRepository events;private final LiveUpdates live;
    public StoreController(ProductRepository products,OrderService orders,AuthController auth,SettingsService settings,ShipmentService shipments,WebhookService webhook,AttemptRepository attempts,EventRepository events,LiveUpdates live){this.products=products;this.orders=orders;this.auth=auth;this.settings=settings;this.shipments=shipments;this.webhook=webhook;this.attempts=attempts;this.events=events;this.live=live;}
    @GetMapping("/api/catalog") public Map<String,Object> catalog(){return Map.of("products",products.findAll(),"districts",OrderService.DISTRICTS);}
    @GetMapping("/api/customer/orders") public Object mine(){return orders.list(auth.current().id);}
    @GetMapping("/api/customer/orders/{id}") public Object mine(@PathVariable UUID id){return orders.get(id,auth.current().id);}
    @PostMapping("/api/customer/orders") @ResponseStatus(HttpStatus.CREATED) public Object checkout(@Valid @RequestBody OrderService.Checkout body){return orders.create(auth.current().id,body);}
    @GetMapping("/api/admin/orders") public Object all(){return orders.list(null);}
    @GetMapping("/api/admin/orders/{id}") public Object one(@PathVariable UUID id){return orders.get(id,null);}
    @PostMapping("/api/admin/orders/{id}/shipment") public Object book(@PathVariable UUID id,@RequestBody Map<String,Boolean> body){return shipments.book(id,Boolean.TRUE.equals(body.get("verifyDuplicate")));}
    @GetMapping("/api/admin/integration") public Object integration(){return settings.view();}
    @PutMapping("/api/admin/integration") public Object configure(@RequestBody SettingsService.Update body){return settings.update(body);}
    @PostMapping("/api/admin/integration/check") public Object check(){shipments.check();return Map.of("connected",true);}
    @PostMapping("/api/admin/failures") public Object failures(@RequestBody Map<String,String> body){settings.failures(body.getOrDefault("mode",""));return settings.view();}
    @GetMapping("/api/admin/webhooks") public Object webhooks(){return Map.of("events",events.findTop100ByOrderByReceivedAtDesc(),"attempts",attempts.findTop100ByOrderByReceivedAtDesc());}
    @GetMapping(value={"/api/admin/live","/api/customer/live"},produces=MediaType.TEXT_EVENT_STREAM_VALUE) public SseEmitter live(HttpServletRequest request){var user=auth.current();return live.connect(user.id,user.role.equals("ADMIN"),request.getSession(false));}
    @PostMapping("/webhooks/swiftdrop") public ResponseEntity<Void> webhook(@RequestBody byte[] body,
        @RequestHeader(value="X-SwiftDrop-Timestamp",required=false) String timestamp,@RequestHeader(value="X-SwiftDrop-Signature",required=false) String signature,
        @RequestHeader(value="X-SwiftDrop-Event-Id",required=false) String id){return ResponseEntity.status(webhook.receive(body,timestamp,signature,id)).cacheControl(CacheControl.noStore()).build();}
}
