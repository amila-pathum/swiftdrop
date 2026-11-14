package com.swiftdrop.pricing.exception;

import java.util.UUID;

public class ZoneNotFoundException extends RuntimeException {
    public ZoneNotFoundException(String city) {
        super("No active zone found for city: " + city);
    }
    public ZoneNotFoundException(UUID id) {
        super("Zone not found: " + id);
    }
}
