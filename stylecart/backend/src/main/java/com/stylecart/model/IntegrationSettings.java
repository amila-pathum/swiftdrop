package com.stylecart.model;
import jakarta.persistence.*;
@Entity @Table(name="integration_settings")
public class IntegrationSettings {
    @Id public Long id=1L;
    @Column(name="gateway_url",nullable=false) public String gatewayUrl;
    @Column(name="api_key_cipher",columnDefinition="text") public String apiKeyCipher;
    @Column(name="secret_cipher",columnDefinition="text") public String secretCipher;
    @Column(name="pickup_name",nullable=false) public String pickupName="";
    @Column(name="pickup_phone",nullable=false) public String pickupPhone="";
    @Column(name="pickup_address",nullable=false) public String pickupAddress="";
    @Column(name="pickup_city",nullable=false) public String pickupCity="Colombo";
    @Column(name="pickup_postal",nullable=false) public String pickupPostal="";
    @Column(name="failure_mode",nullable=false) public String failureMode="NORMAL";
    @Column(name="failures_remaining",nullable=false) public int failuresRemaining;
}
