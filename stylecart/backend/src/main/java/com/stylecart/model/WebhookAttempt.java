package com.stylecart.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="webhook_attempts")
public class WebhookAttempt {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(name="event_id") public UUID eventId;
    @Column(name="received_at",nullable=false) public Instant receivedAt=Instant.now();
    @Column(name="http_status",nullable=false) public int httpStatus;
    @Column(nullable=false,length=40) public String outcome;
}
