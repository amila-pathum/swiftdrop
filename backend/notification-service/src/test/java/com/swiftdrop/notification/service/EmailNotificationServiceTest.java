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
        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                "parcel.status-changed",
                Instant.now(),
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
        assertThat(message.getSubject()).contains("SD-123 is on the way (In Transit)");
        assertThat(message.getText()).contains("SD-123");
        assertThat(message.getText()).contains("In Transit");
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
    }

    @Test
    void sendParcelUpdateEmail_withUnresolvedCustomerId_fallsBackToConfiguredRecipient() {
        when(identityServiceClient.findEmailByCustomerId("unknown-cust"))
                .thenReturn(Optional.empty());

        ParcelStatusChangedEvent event = new ParcelStatusChangedEvent(
                UUID.randomUUID(),
                "parcel.status-changed",
                Instant.now(),
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
        assertThat(message.getSubject()).contains("SD-456 has been delivered!");
        assertThat(message.getText()).contains("Delivered");
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
}
