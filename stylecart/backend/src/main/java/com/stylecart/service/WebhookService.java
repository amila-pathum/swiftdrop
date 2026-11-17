package com.stylecart.service;
import com.stylecart.model.*;
import com.stylecart.repo.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;
@Service
public class WebhookService {
    private final SettingsRepository settings;private final EventRepository received;private final AttemptRepository attempts;
    private final OrderRepository orders;private final SecretBox secrets;private final ObjectMapper json;private final ApplicationEventPublisher events;
    public WebhookService(SettingsRepository settings,EventRepository received,AttemptRepository attempts,OrderRepository orders,SecretBox secrets,ObjectMapper json,ApplicationEventPublisher events){this.settings=settings;this.received=received;this.attempts=attempts;this.orders=orders;this.secrets=secrets;this.json=json;this.events=events;}
    @Transactional public int receive(byte[] raw,String timestamp,String signature,String eventHeader){
        var config=settings.lock().orElseThrow();if(config.secretCipher==null)return attempt(null,503,"SECRET_NOT_CONFIGURED");
        if(raw.length>65536)return attempt(null,413,"BODY_TOO_LARGE");
        if(!valid(secrets.decrypt(config.secretCipher),raw,timestamp,signature,Instant.now()))return attempt(null,401,"INVALID_SIGNATURE");
        Payload p;
        try{p=json.readValue(raw,Payload.class);if(p==null||p.eventId()==null||!p.eventId().toString().equals(eventHeader)||p.occurredAt()==null
            ||!Set.of("ParcelStatusChanged","WebhookTest").contains(p.eventType())
            ||(!"WebhookTest".equals(p.eventType())&&(p.newStatus()==null||!OrderService.STATUSES.contains(p.newStatus())||p.trackingNumber()==null||p.trackingNumber().isBlank()||p.trackingNumber().length()>100||p.merchantOrderReference()!=null&&p.merchantOrderReference().length()>100)))return attempt(null,400,"INVALID_PAYLOAD");
        }catch(RuntimeException e){return attempt(null,400,"INVALID_PAYLOAD");}
        String hash;try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));}catch(Exception e){throw new IllegalStateException(e);}
        var duplicate=received.findById(p.eventId());
        if(duplicate.isPresent())return duplicate.get().payloadHash.equals(hash)?attempt(p.eventId(),204,"DUPLICATE"):attempt(p.eventId(),409,"EVENT_CONTENT_CONFLICT");
        if(config.failureMode.equals("ALWAYS")||config.failureMode.equals("NEXT_TWO")&&config.failuresRemaining>0){if(config.failureMode.equals("NEXT_TWO"))config.failuresRemaining--;return attempt(p.eventId(),500,"SIMULATED_FAILURE");}
        StoreOrder match=null;
        if(!p.eventType().equals("WebhookTest")){
            var byRef=p.merchantOrderReference()==null?Optional.<StoreOrder>empty():orders.findByReference(p.merchantOrderReference());
            var byTracking=orders.findByTrackingNumber(p.trackingNumber());
            if(byRef.isPresent()&&byTracking.isPresent()&&!byRef.get().id.equals(byTracking.get().id))return attempt(p.eventId(),409,"ORDER_MISMATCH");
            var candidate=byRef.or(()->byTracking);
            if(candidate.isPresent()){
                match=orders.lock(candidate.get().id).orElseThrow();
                if(match.trackingNumber!=null&&!match.trackingNumber.equals(p.trackingNumber())||p.merchantOrderReference()!=null&&!match.reference.equals(p.merchantOrderReference()))return attempt(p.eventId(),409,"ORDER_MISMATCH");
            }
        }
        var e=new WebhookEvent();e.eventId=p.eventId();e.payloadHash=hash;e.eventType=p.eventType();e.trackingNumber=p.trackingNumber();e.merchantReference=p.merchantOrderReference();e.newStatus=p.newStatus();e.occurredAt=p.occurredAt();
        if(match!=null){e.orderId=match.id;BookingTransactions.apply(match,e);events.publishEvent(new LiveUpdates.Changed(match.customerId,match.id));}
        received.saveAndFlush(e);return attempt(p.eventId(),204,p.eventType().equals("WebhookTest")?"TEST_ACCEPTED":match==null?"PENDING_MATCH":"ACCEPTED");
    }
    private int attempt(UUID eventId,int code,String outcome){var a=new WebhookAttempt();a.eventId=eventId;a.httpStatus=code;a.outcome=outcome;attempts.save(a);events.publishEvent(new LiveUpdates.Changed(null,null));return code;}
    public static boolean valid(String secret,byte[] body,String timestamp,String signature,Instant now){
        try{if(timestamp==null||!timestamp.matches("[0-9]{1,13}")||Math.abs(now.getEpochSecond()-Long.parseLong(timestamp))>300||signature==null||!signature.matches("sha256=[a-f0-9]{64}"))return false;
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(mac.doFinal(body),HexFormat.of().parseHex(signature.substring(7)));
        }catch(Exception e){return false;}
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    public record Payload(UUID eventId,String eventType,Instant occurredAt,String trackingNumber,String merchantOrderReference,String newStatus){}
}
