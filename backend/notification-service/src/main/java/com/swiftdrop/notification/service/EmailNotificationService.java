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
     * Converts raw system statuses like "IN_TRANSIT" into friendly user labels like "In Transit".
     */
    public static String formatFriendlyStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Updated";
        }
        return switch (status.trim().toUpperCase()) {
            case "CREATED" -> "Order Created";
            case "PENDING" -> "Pending Pickup";
            case "PICKED_UP" -> "Picked Up";
            case "IN_TRANSIT" -> "In Transit";
            case "OUT_FOR_DELIVERY" -> "Out for Delivery";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Cancelled";
            case "RETURNED" -> "Returned to Sender";
            case "FAILED_DELIVERY" -> "Delivery Attempt Failed";
            default -> Arrays.stream(status.split("_"))
                    .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        };
    }

    /**
     * Builds a user-friendly, descriptive email subject.
     */
    public static String buildSubject(String trackingNumber, String newStatus) {
        String friendly = formatFriendlyStatus(newStatus);
        return switch (newStatus != null ? newStatus.toUpperCase() : "") {
            case "DELIVERED" -> String.format("SwiftDrop: Your parcel %s has been delivered!", trackingNumber);
            case "OUT_FOR_DELIVERY" -> String.format("SwiftDrop: Your parcel %s is out for delivery today", trackingNumber);
            case "PICKED_UP" -> String.format("SwiftDrop: Your parcel %s has been picked up", trackingNumber);
            case "IN_TRANSIT" -> String.format("SwiftDrop: Your parcel %s is on the way (In Transit)", trackingNumber);
            default -> String.format("SwiftDrop Update: Parcel %s is now %s", trackingNumber, friendly);
        };
    }

    /**
     * Builds a clean, professional, and user-friendly email message body.
     */
    public static String buildBody(String trackingNumber, String oldStatus, String newStatus, Instant occurredAt) {
        String friendlyNew = formatFriendlyStatus(newStatus);
        String friendlyOld = formatFriendlyStatus(oldStatus);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy 'at' hh:mm a 'UTC'")
                .withZone(ZoneId.of("UTC"));
        String formattedTime = occurredAt != null ? formatter.format(occurredAt) : "Recently";

        String headline = switch (newStatus != null ? newStatus.toUpperCase() : "") {
            case "DELIVERED" -> "Great news! Your package has been successfully delivered.";
            case "OUT_FOR_DELIVERY" -> "Get ready! Your courier is out for delivery with your package.";
            case "IN_TRANSIT" -> "Your package is on its way to the delivery destination.";
            case "PICKED_UP" -> "We have collected your parcel and it is being prepared for transit.";
            case "CANCELLED" -> "Your parcel delivery has been cancelled.";
            default -> "There has been an update regarding your parcel.";
        };

        StringBuilder sb = new StringBuilder();
        sb.append("Hello,\n\n");
        sb.append(headline).append("\n\n");
        sb.append("--------------------------------------------------\n");
        sb.append("Shipment Details\n");
        sb.append("--------------------------------------------------\n");
        sb.append("• Tracking Number : ").append(trackingNumber).append("\n");
        sb.append("• Current Status  : ").append(friendlyNew).append("\n");
        if (oldStatus != null && !oldStatus.isBlank()) {
            sb.append("• Previous Status : ").append(friendlyOld).append("\n");
        }
        sb.append("• Updated At      : ").append(formattedTime).append("\n\n");
        sb.append("You can track live delivery updates anytime on our tracking portal:\n");
        sb.append("http://localhost:3000/track\n\n");
        sb.append("Thank you for choosing SwiftDrop!\n\n");
        sb.append("Warm regards,\n");
        sb.append("SwiftDrop Customer Delivery Team");

        return sb.toString();
    }

    /**
     * Sends an email notification to the customer informing them of parcel status change.
     */
    public void sendParcelUpdateEmail(ParcelStatusChangedEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String recipientEmail = resolveCustomerEmail(event.customerId());

        String subject = buildSubject(event.trackingNumber(), event.newStatus());
        String body = buildBody(event.trackingNumber(), event.oldStatus(), event.newStatus(), event.occurredAt());

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
