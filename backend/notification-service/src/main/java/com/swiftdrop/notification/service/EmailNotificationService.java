package com.swiftdrop.notification.service;

import com.swiftdrop.notification.client.IdentityServiceClient;
import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final IdentityServiceClient identityServiceClient;
    private final String mailHost;
    private final int mailPort;
    private final String mailFrom;
    private final String testRecipient;
    private final String mailUsername;

    public EmailNotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Autowired(required = false) IdentityServiceClient identityServiceClient,
            @Value("${notification.mail.host:localhost}") String mailHost,
            @Value("${notification.mail.port:587}") int mailPort,
            @Value("${notification.mail.from:notifications@swiftdrop.com}") String mailFrom,
            @Value("${notification.mail.test-recipient:}") String testRecipient,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.identityServiceClient = identityServiceClient;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.mailFrom = mailFrom;
        this.testRecipient = testRecipient;
        this.mailUsername = mailUsername;
    }

    /**
     * Resolves the target email address for a given customerId.
     * 1. If customerId is already an email address (e.g. test events or OAuth logins), uses it directly.
     * 2. Queries identity-service to resolve the user's registered email address.
     * 3. Otherwise falls back to configured test recipient or MAIL_USERNAME for local development testing.
     */
    public String resolveCustomerEmail(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            return null;
        }

        // 1. Direct email in customerId
        if (customerId.contains("@")) {
            return customerId.trim();
        }

        // 2. Query Identity Service
        if (identityServiceClient != null) {
            Optional<String> emailOpt = identityServiceClient.findEmailByCustomerId(customerId);
            if (emailOpt.isPresent()) {
                log.info("Resolved customer email '{}' from identity-service for customerId '{}'",
                        emailOpt.get(), customerId);
                return emailOpt.get();
            }
        }

        // 3. Fallback for testing / dev environment
        if (StringUtils.hasText(testRecipient) && testRecipient.contains("@")) {
            log.info("Using configured test-recipient '{}' for customerId '{}'", testRecipient, customerId);
            return testRecipient.trim();
        }
        if (StringUtils.hasText(mailUsername) && mailUsername.contains("@")) {
            log.info("Using MAIL_USERNAME '{}' as recipient for customerId '{}'", mailUsername, customerId);
            return mailUsername.trim();
        }

        return null;
    }

    /**
     * Converts raw system statuses like "IN_TRANSIT" into friendly status words like "In Transit".
     */
    public static String formatFriendlyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Updated";
        }
        return switch (status.trim().toUpperCase()) {
            case "CREATED" -> "Created";
            case "PENDING" -> "Pending";
            case "PICKED_UP" -> "Picked Up";
            case "IN_TRANSIT" -> "In Transit";
            case "OUT_FOR_DELIVERY" -> "Out for Delivery";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Cancelled";
            case "RETURNED" -> "Returned";
            case "FAILED_DELIVERY" -> "Delivery Failed";
            default -> Arrays.stream(status.split("_"))
                    .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        };
    }

    /**
     * Sends an email notification to the customer informing them of parcel status change.
     */
    public void sendParcelUpdateEmail(ParcelStatusChangedEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String recipientEmail = resolveCustomerEmail(event.customerId());

        String friendlyNewStatus = formatFriendlyStatus(event.newStatus());
        String friendlyOldStatus = formatFriendlyStatus(event.oldStatus());

        String subject = String.format("SwiftDrop Update: Parcel %s is now %s",
                event.trackingNumber(), friendlyNewStatus);

        String body = String.format(
                "Hello,\n\nYour parcel with tracking number %s has changed status from %s to %s at %s UTC.\n" +
                "You can view live updates on the SwiftDrop tracking portal.\n\nThank you,\nSwiftDrop Team",
                event.trackingNumber(), friendlyOldStatus, friendlyNewStatus, event.occurredAt()
        );

        if (!StringUtils.hasText(recipientEmail)) {
            log.warn("No recipient email could be resolved for customerId={}. Skipping email dispatch.",
                    event.customerId());
            return;
        }

        log.info("Sending email notification: host={}:{}, from={}, to={}, customerId={}, trackingNumber={}, subject='{}'",
                mailHost, mailPort, mailFrom, recipientEmail, event.customerId(), event.trackingNumber(), subject);

        if (mailSender == null) {
            log.warn("JavaMailSender is not configured. Email to {} was not sent.", recipientEmail);
            return;
        }

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(mailFrom);
            mailMessage.setTo(recipientEmail);
            mailMessage.setSubject(subject);
            mailMessage.setText(body);

            mailSender.send(mailMessage);
            log.info("Email notification successfully sent to {} for parcel {}",
                    recipientEmail, event.trackingNumber());
        } catch (Exception ex) {
            log.error("Failed to send email to {} for parcel {}: {}",
                    recipientEmail, event.trackingNumber(), ex.getMessage(), ex);
        }
    }
}
