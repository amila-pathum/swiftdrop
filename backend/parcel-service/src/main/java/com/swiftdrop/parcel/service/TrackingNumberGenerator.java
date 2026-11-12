package com.swiftdrop.parcel.service;

import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class TrackingNumberGenerator {
    // SD + 32 hexadecimal characters = 34; fits tracking_number VARCHAR(40).
    // Database UNIQUE constraint is the final protection against rare collisions.
    public String generate() {
        return "SD" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
