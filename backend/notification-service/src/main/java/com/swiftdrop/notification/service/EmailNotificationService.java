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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailNotificationService {

    public static final ZoneId SRI_LANKA_ZONE = ZoneId.of("Asia/Colombo");
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

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
     * 1. If customerId is already an email address (e.g. test events or OAuth
     * logins), uses it directly.
     * 2. Queries identity-service to resolve the user's registered email address.
     * 3. Otherwise falls back to configured test recipient or MAIL_USERNAME for
     * local development testing.
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
     * Converts raw system statuses like "IN_TRANSIT" into friendly status words
     * like "In Transit".
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
            case "FAILED", "FAILED_DELIVERY" -> "Delivery Failed";
            default -> Arrays.stream(status.split("_"))
                    .map(word -> word.isEmpty() ? ""
                            : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
        };
    }

    /**
     * Generates a customer-friendly, professional email subject based on the new
     * parcel status.
     * Technical transitions and old status are never displayed.
     */
    public static String resolveEmailSubject(String newStatus) {
        if (!StringUtils.hasText(newStatus)) {
            return "SwiftDrop – Parcel Status Update";
        }
        return switch (newStatus.trim().toUpperCase()) {
            case "DELIVERED" -> "SwiftDrop – Parcel Successfully Delivered";
            case "OUT_FOR_DELIVERY" -> "SwiftDrop – Parcel Out for Delivery";
            case "IN_TRANSIT" -> "SwiftDrop – Parcel In Transit";
            case "PICKED_UP" -> "SwiftDrop – Parcel Picked Up";
            case "CREATED", "PENDING" -> "SwiftDrop – Parcel Registered";
            case "FAILED", "FAILED_DELIVERY" -> "SwiftDrop – Delivery Attempt Unsuccessful";
            case "CANCELLED" -> "SwiftDrop – Parcel Delivery Cancelled";
            case "RETURNED" -> "SwiftDrop – Parcel Being Returned";
            default -> "SwiftDrop – Parcel Status Update";
        };
    }

    /**
     * Generates the primary status sentence for the customer email.
     * Converts UTC instant to Sri Lanka Standard Time (Asia/Colombo).
     */
    public static String resolveStatusSentence(String newStatus, String trackingNumber, ZonedDateTime slTime) {
        String tracking = StringUtils.hasText(trackingNumber) ? trackingNumber.trim() : "your parcel";
        String deliveryDate = DATE_FORMATTER.format(slTime);
        String deliveryTime = TIME_FORMATTER.format(slTime);

        if (!StringUtils.hasText(newStatus)) {
            return String.format("Your parcel %s status has been updated as of %s, at %s (Sri Lanka time).",
                    tracking, deliveryDate, deliveryTime);
        }

        return switch (newStatus.trim().toUpperCase()) {
            case "DELIVERED" ->
                String.format("Your parcel %s has been successfully delivered on %s, at %s (Sri Lanka time).",
                        tracking, deliveryDate, deliveryTime);
            case "OUT_FOR_DELIVERY" ->
                String.format(
                        "Your parcel %s is out for delivery as of %s, at %s (Sri Lanka time) and will arrive soon.",
                        tracking, deliveryDate, deliveryTime);
            case "IN_TRANSIT" ->
                String.format("Your parcel %s is on the way to the delivery location as of %s, at %s (Sri Lanka time).",
                        tracking, deliveryDate, deliveryTime);
            case "PICKED_UP" ->
                String.format("Your parcel %s has been picked up by the courier on %s, at %s (Sri Lanka time).",
                        tracking, deliveryDate, deliveryTime);
            case "CREATED", "PENDING" ->
                String.format(
                        "Your parcel %s has been registered on %s, at %s (Sri Lanka time) and is being processed.",
                        tracking, deliveryDate, deliveryTime);
            case "FAILED", "FAILED_DELIVERY" ->
                String.format(
                        "A delivery attempt for your parcel %s was unsuccessful on %s, at %s (Sri Lanka time). Our team will attempt delivery again soon.",
                        tracking, deliveryDate, deliveryTime);
            case "CANCELLED" ->
                String.format("Your delivery for parcel %s has been cancelled on %s, at %s (Sri Lanka time).",
                        tracking, deliveryDate, deliveryTime);
            case "RETURNED" ->
                String.format("Your parcel %s is being returned to the sender as of %s, at %s (Sri Lanka time).",
                        tracking, deliveryDate, deliveryTime);
            default ->
                String.format("Your parcel %s status is now %s as of %s, at %s (Sri Lanka time).",
                        tracking, formatFriendlyStatus(newStatus), deliveryDate, deliveryTime);
        };
    }

    /**
     * Constructs the full email body with salutation, status description, and
     * closing sign-off.
     */
    public static String resolveEmailBody(String newStatus, String trackingNumber, Instant occurredAt) {
        Instant eventTime = occurredAt != null ? occurredAt : Instant.now();
        ZonedDateTime slTime = eventTime.atZone(SRI_LANKA_ZONE);

        String statusSentence = resolveStatusSentence(newStatus, trackingNumber, slTime);

        return String.format(
                "Dear Customer,\n\n" +
                        "%s\n\n" +
                        "Thank you for choosing SwiftDrop. We appreciate your trust in our service.\n\n" +
                        "Best regards,\n" +
                        "SwiftDrop Team",
                statusSentence);
    }

    /**
     * Sends an email notification to the customer informing them of parcel status
     * change.
     */
    public void sendParcelUpdateEmail(ParcelStatusChangedEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        String recipientEmail = resolveCustomerEmail(event.customerId());

        String subject = resolveEmailSubject(event.newStatus());
        String body = resolveEmailBody(event.newStatus(), event.trackingNumber(), event.occurredAt());

        if (!StringUtils.hasText(recipientEmail)) {
            log.warn("No recipient email could be resolved for customerId={}. Skipping email dispatch.",
                    event.customerId());
            return;
        }

        log.info(
                "Sending email notification: host={}:{}, from={}, to={}, customerId={}, trackingNumber={}, subject='{}'",
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
