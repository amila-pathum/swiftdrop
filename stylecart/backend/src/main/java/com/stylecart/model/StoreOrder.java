package com.stylecart.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity @Table(name="store_orders")
public class StoreOrder {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @Column(nullable=false,unique=true,length=100) public String reference;
    @Column(name="customer_id",nullable=false) public Long customerId;
    @Column(name="receiver_name",nullable=false,length=150) public String receiverName;
    @Column(nullable=false,length=30) public String phone;
    @Column(nullable=false,length=255) public String address;
    @Column(nullable=false,length=100) public String city;
    @Column(name="postal_code",length=20) public String postalCode;
    @Column(nullable=false,precision=12,scale=2) public BigDecimal subtotal;
    @Column(name="shipping_charge",precision=12,scale=2) public BigDecimal shippingCharge;
    @Column(name="shipment_state",nullable=false,length=30) public String shipmentState="NOT_BOOKED";
    @Column(name="tracking_number",unique=true,length=100) public String trackingNumber;
    @Column(name="delivery_status",length=30) public String deliveryStatus;
    @Column(name="status_at") public Instant statusAt;
    @Column(name="submitted_at") public Instant submittedAt;
    @Column(name="request_payload",columnDefinition="text") public String requestPayload;
    @Column(name="last_error",length=400) public String lastError;
    @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
    @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("id") public List<OrderItem> items=new ArrayList<>();
}
