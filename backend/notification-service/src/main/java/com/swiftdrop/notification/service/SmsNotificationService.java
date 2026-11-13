package com.swiftdrop.notification.service;

import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
public class SmsNotificationService {

    private final boolean smsEnabled;

    public SmsNotificationService(
            @Value("${notification.sms.enabled:true}") boolean smsEnabled) {
        this.smsEnabled = smsEnabled;
    }

    /**
     * Dispatches mock SMS notification to the customer.
     * In accordance with privacy and security requirements, phone numbers are never logged.
     */
    public void sendParcelUpdateSms(ParcelStatusChangedEvent event) {
        if (!smsEnabled) {
            return;
        }

        Objects.requireNonNull(event, "event must not be null");

        String smsText = String.format("SwiftDrop: Parcel %s is now %s.",
                event.trackingNumber(), event.newStatus());

        // Privacy rule: Never log the phone number
        log.info("Mock SMS dispatched: customerId={}, trackingNumber={}, newStatus={}, message='{}'",
                event.customerId(), event.trackingNumber(), event.newStatus(), smsText);
    }
}

