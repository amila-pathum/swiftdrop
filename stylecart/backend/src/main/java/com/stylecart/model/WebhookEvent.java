package com.stylecart.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="webhook_events")
public class WebhookEvent {
    @Id @Column(name="event_id") public UUID eventId;
    @Column(name="payload_hash",nullable=false,length=64) public String payloadHash;
    @Column(name="event_type",nullable=false) public String eventType;
    @Column(name="tracking_number",length=100) public String trackingNumber;
    @Column(name="merchant_reference",length=100) public String merchantReference;
    @Column(name="new_status",length=30) public String newStatus;
    @Column(name="occurred_at",nullable=false) public Instant occurredAt;
    @Column(name="received_at",nullable=false) public Instant receivedAt=Instant.now();
    @Column(name="order_id") public UUID orderId;
}
