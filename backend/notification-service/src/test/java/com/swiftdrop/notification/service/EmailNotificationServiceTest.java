package com.swiftdrop.notification.service;

import com.swiftdrop.notification.client.IdentityServiceClient;
import com.swiftdrop.notification.event.ParcelStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private IdentityServiceClient identityServiceClient;

    private EmailNotificationService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailNotificationService(
                mailSender,
                identityServiceClient,
                "smtp.gmail.com",
                587,
                "swiftdrop.notifications@gmail.com",
                "fallback@example.com",
                "swiftdrop.notifications@gmail.com"
        );
    }

    @Test
    void sendParcelUpdateEmail_withDirectEmailCustomerId_sendsToCustomer() {
        Instant fixedInstant = Instant.parse("2026-11-14T14:52:00Z"); // 8:22 PM in Sri Lanka (UTC+5:30)
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                "parcel.status-changed",
                fixedInstant,
                "SD-123",
                "p-1",
                null,
                null,
                "customer@example.com",
                null,
                "ORDERED",
                "IN_TRANSIT"
        );

        emailService.sendParcelUpdateEmail(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("customer@example.com");
        assertThat(message.getSubject()).isEqualTo("SwiftDrop – Parcel In Transit");
        assertThat(message.getText()).contains("Dear Customer,");
        assertThat(message.getText()).contains("Your parcel **SD-123** is on the way to the delivery location as of November 14, 2026, at 8:22 PM (Sri Lanka time).");
        assertThat(message.getText()).contains("Thank you for choosing SwiftDrop. We appreciate your trust in our service.");
        assertThat(message.getText()).contains("Best regards,\n**SwiftDrop Team**");
        assertThat(message.getText()).doesNotContain("ORDERED");
        assertThat(message.getText()).doesNotContain("from ORDERED to IN_TRANSIT");
    }

    @Test
    void sendParcelUpdateEmail_withResolvedEmailFromIdentityService_sendsToResolvedEmail() {
        when(identityServiceClient.findEmailByCustomerId("100"))
                .thenReturn(Optional.of("alice@swiftdrop.com"));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                "parcel.status-changed",
                Instant.now(),
                "SD-999",
                "p-99",
                null,
                null,
                "100",
                null,
                "CREATED",
                "PICKED_UP"
        );

        emailService.sendParcelUpdateEmail(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("alice@swiftdrop.com");
        assertThat(message.getSubject()).isEqualTo("SwiftDrop – Parcel Picked Up");
        assertThat(message.getText()).contains("Your parcel **SD-999** has been picked up by the courier");
        assertThat(message.getText()).contains("(Sri Lanka time)");
        assertThat(message.getText()).doesNotContain("CREATED");
    }

    @Test
    void sendParcelUpdateEmail_withUnresolvedCustomerId_fallsBackToConfiguredRecipient() {
        when(identityServiceClient.findEmailByCustomerId("unknown-cust"))
                .thenReturn(Optional.empty());

        Instant fixedInstant = Instant.parse("2026-11-14T14:52:00Z"); // 8:22 PM in Sri Lanka (UTC+5:30)
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                "parcel.status-changed",
                fixedInstant,
                "SD-456",
                "p-2",
                null,
                null,
                "unknown-cust",
                null,
                "IN_TRANSIT",
                "DELIVERED"
        );

        emailService.sendParcelUpdateEmail(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("fallback@example.com");
        assertThat(message.getSubject()).isEqualTo("SwiftDrop – Parcel Successfully Delivered");
        assertThat(message.getText()).isEqualTo(
                "Dear Customer,\n\n" +
                "Your parcel **SD-456** has been successfully delivered on November 14, 2026, at 8:22 PM (Sri Lanka time).\n\n" +
                "Thank you for choosing SwiftDrop. We appreciate your trust in our service.\n\n" +
                "Best regards,\n" +
                "**SwiftDrop Team**"
        );
        assertThat(message.getText()).doesNotContain("IN_TRANSIT");
    }

    @Test
    void sendParcelUpdateEmail_whenMailSenderThrows_doesNotPropagateException() {
        doThrow(new MailSendException("SMTP timeout")).when(mailSender).send(any(SimpleMailMessage.class));

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                "parcel.status-changed",
                Instant.now(),
                "SD-789",
                "p-3",
                null,
                null,
                "user@example.com",
                null,
                "PENDING",
                "OUT_FOR_DELIVERY"
        );

        assertDoesNotThrow(() -> emailService.sendParcelUpdateEmail(event));
    }

    @Test
    void resolveEmailSubject_generatesCustomerFriendlySubjectsWithoutTransitions() {
        assertThat(EmailNotificationService.resolveEmailSubject("DELIVERED"))
                .isEqualTo("SwiftDrop – Parcel Successfully Delivered");
        assertThat(EmailNotificationService.resolveEmailSubject("OUT_FOR_DELIVERY"))
                .isEqualTo("SwiftDrop – Parcel Out for Delivery");
        assertThat(EmailNotificationService.resolveEmailSubject("IN_TRANSIT"))
                .isEqualTo("SwiftDrop – Parcel In Transit");
        assertThat(EmailNotificationService.resolveEmailSubject("PICKED_UP"))
                .isEqualTo("SwiftDrop – Parcel Picked Up");
        assertThat(EmailNotificationService.resolveEmailSubject("CREATED"))
                .isEqualTo("SwiftDrop – Parcel Registered");
        assertThat(EmailNotificationService.resolveEmailSubject("PENDING"))
                .isEqualTo("SwiftDrop – Parcel Registered");
        assertThat(EmailNotificationService.resolveEmailSubject("FAILED"))
                .isEqualTo("SwiftDrop – Delivery Attempt Unsuccessful");
        assertThat(EmailNotificationService.resolveEmailSubject("CANCELLED"))
                .isEqualTo("SwiftDrop – Parcel Delivery Cancelled");
        assertThat(EmailNotificationService.resolveEmailSubject("RETURNED"))
                .isEqualTo("SwiftDrop – Parcel Being Returned");
        assertThat(EmailNotificationService.resolveEmailSubject(null))
                .isEqualTo("SwiftDrop – Parcel Status Update");
    }

    @Test
    void resolveEmailBody_convertsUtcToSriLankaTimeCorrectly() {
        // 2026-11-15T02:30:00Z + 05:30 -> 2026-11-15 08:00 AM (Sri Lanka)
        Instant utcInstant = Instant.parse("2026-11-15T02:30:00Z");

        String body = EmailNotificationService.resolveEmailBody("DELIVERED", "SD-2026-9003", utcInstant);

        assertThat(body).isEqualTo(
                "Dear Customer,\n\n" +
                "Your parcel **SD-2026-9003** has been successfully delivered on November 15, 2026, at 8:00 AM (Sri Lanka time).\n\n" +
                "Thank you for choosing SwiftDrop. We appreciate your trust in our service.\n\n" +
                "Best regards,\n" +
                "**SwiftDrop Team**"
        );
    }

    @Test
    void resolveEmailBody_handlesMidnightCrossingCorrectly() {
        // 2026-11-14T20:00:00Z + 05:30 -> 2026-11-15 01:30 AM (crosses midnight into next day)
        Instant utcInstant = Instant.parse("2026-11-14T20:00:00Z");

        String body = EmailNotificationService.resolveEmailBody("OUT_FOR_DELIVERY", "SD-9001", utcInstant);

        assertThat(body).contains("Your parcel **SD-9001** is out for delivery as of November 15, 2026, at 1:30 AM (Sri Lanka time) and will arrive soon.");
        assertThat(body).contains("Dear Customer,");
        assertThat(body).contains("**SwiftDrop Team**");
    }
}
