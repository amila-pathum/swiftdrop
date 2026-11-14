package com.swiftdrop.pricing.exception;

import java.util.UUID;

public class ZoneNotFoundException extends RuntimeException {
    public ZoneNotFoundException(String locality) {
        super("No active zone covers: " + locality);
    }
    public ZoneNotFoundException(UUID id) {
        super("Zone not found: " + id);
    }
}
