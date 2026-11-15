package com.stylecart.service;
import com.stylecart.model.*;
import com.stylecart.repo.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
@Service
public class BookingTransactions {
    private final OrderRepository orders;private final SettingsRepository settings;private final EventRepository received;
    private final ApplicationEventPublisher events;private final ObjectMapper json;
    public BookingTransactions(OrderRepository orders,SettingsRepository settings,EventRepository received,ApplicationEventPublisher events,ObjectMapper json){this.orders=orders;this.settings=settings;this.received=received;this.events=events;this.json=json;}
    @Transactional public Job prepare(UUID id,boolean verifyDuplicate){
        var o=orders.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(o.trackingNumber!=null&&!verifyDuplicate)return new Job(o.id,o.reference,null);
        if(o.shipmentState.equals("SUBMITTING")&&o.submittedAt!=null&&o.submittedAt.isAfter(Instant.now().minusSeconds(45)))throw new ResponseStatusException(HttpStatus.CONFLICT,"This order is already being booked. Please wait.");
        if(o.requestPayload==null){
            var s=settings.findById(1L).orElseThrow();
            if(s.pickupName.isBlank()||s.pickupAddress.isBlank()||!s.pickupPhone.matches("^(?=(?:[^0-9]*[0-9]){7})[+0-9 ()-]{7,30}$")||!OrderService.DISTRICTS.contains(s.pickupCity))throw new ResponseStatusException(HttpStatus.CONFLICT,"Save valid shop pickup details in Integration first.");
            var pickup=Map.of("name",s.pickupName,"phone",s.pickupPhone,"address",s.pickupAddress,"city",s.pickupCity,"postalCode",s.pickupPostal);
            var receiver=Map.of("name",o.receiverName,"phone",o.phone,"address",o.address,"city",o.city,"postalCode",o.postalCode);
            BigDecimal weight=o.items.stream().map(i->i.weight.multiply(BigDecimal.valueOf(i.quantity))).reduce(BigDecimal.ZERO,BigDecimal::add);
            String description=String.join(", ",o.items.stream().map(i->i.quantity+" x "+i.name).toList());if(description.length()>500)description=description.substring(0,500);
            o.requestPayload=json.writeValueAsString(Map.of("merchantOrderReference",o.reference,"pickup",pickup,"receiver",receiver,"parcel",
                Map.of("description",description,"weightKg",weight,"lengthCm",35,"widthCm",25,"heightCm",12,"size","MEDIUM","serviceType","STANDARD","handlingNote","Clothing. Keep dry.")));
        }
        o.shipmentState="SUBMITTING";o.submittedAt=Instant.now();o.lastError=null;
        events.publishEvent(new LiveUpdates.Changed(o.customerId,o.id));return new Job(o.id,o.reference,o.requestPayload);
    }
    @Transactional public void complete(UUID id,ShipmentService.Reply reply){
        var o=orders.lock(id).orElseThrow();
        if(o.trackingNumber!=null&&!o.trackingNumber.equals(reply.trackingNumber()))throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"SwiftDrop returned a conflicting tracking number. Keep the original reference.");
        o.trackingNumber=reply.trackingNumber();o.shipmentState="BOOKED";o.lastError=null;
        if(o.statusAt==null)o.deliveryStatus=reply.status();
        if(reply.parcel()!=null)o.shippingCharge=reply.parcel().charge();
        for(var e:received.findByTrackingNumberOrderByOccurredAtAsc(o.trackingNumber)){
            if((e.orderId==null||e.orderId.equals(o.id))&&(e.merchantReference==null||e.merchantReference.equals(o.reference))){apply(o,e);e.orderId=o.id;}
        }
        events.publishEvent(new LiveUpdates.Changed(o.customerId,o.id));
    }
    @Transactional public void failed(UUID id,String message){var o=orders.lock(id).orElseThrow();o.shipmentState=o.trackingNumber==null?"NEEDS_REVIEW":"BOOKED";o.lastError=message;events.publishEvent(new LiveUpdates.Changed(o.customerId,o.id));}
    public static void apply(StoreOrder o,WebhookEvent e){
        if(o.trackingNumber==null)o.trackingNumber=e.trackingNumber;
        if(o.statusAt==null||e.occurredAt.isAfter(o.statusAt)){o.deliveryStatus=e.newStatus;o.statusAt=e.occurredAt;}
        o.shipmentState="BOOKED";o.lastError=null;
    }
    public record Job(UUID id,String reference,String payload){@Override public String toString(){return "BookingJob["+id+"]";}}
}
