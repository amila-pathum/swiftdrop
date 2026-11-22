package com.swiftdrop.parcel.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TrackingNumberGenerator {
    // Format: SD followed by 8 digits, e.g. SD10480014.
    // Database UNIQUE constraint is the final protection against rare collisions.
    public String generate() {
        int number = ThreadLocalRandom.current().nextInt(10000000, 100000000);
        return "SD" + number;
    }
}
