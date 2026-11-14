package com.swiftdrop.notification.webhook.consumer;

import com.swiftdrop.notification.webhook.retry.WebhookEventRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebhookParcelStatusConsumerTest {

    private static final UUID EVENT_ID =
            UUID.fromString("11111111-1111-4111-8111-111111111111");

    private static final Instant OCCURRED_AT =
            Instant.parse("2026-11-13T00:00:00Z");

    private final WebhookEventRecorder recorder =
            mock(WebhookEventRecorder.class);

    private final WebhookParcelStatusConsumer consumer =
            new WebhookParcelStatusConsumer(
                    new ObjectMapper(),
                    recorder
            );

    private static final String EVENT = """
            {
              "eventId": "11111111-1111-4111-8111-111111111111",
              "eventType": "ParcelStatusChanged",
              "occurredAt": "2026-11-13T00:00:00Z",
              "trackingNumber": "SD-100",
              "parcelId": "parcel-1",
              "merchantId": "merchant-1",
              "merchantOrderReference": "SHOP-100",
              "customerId": "customer-1",
              "courierId": null,
              "oldStatus": "IN_TRANSIT",
              "newStatus": "DELIVERED"
            }
            """;

    @Test
    void merchantEventIsPassedToRecorder() throws Exception {
        consumer.consume(bytes(EVENT));

        verify(recorder).record(
                EVENT_ID,
                OCCURRED_AT,
                "merchant-1",
                "SD-100",
                "SHOP-100",
                "DELIVERED"
        );
        verifyNoMoreInteractions(recorder);
    }

    @Test
    void customerOnlyEventDoesNotCreateWebhook() throws Exception {
        String event = EVENT.replace(
                "\"merchantId\": \"merchant-1\"",
                "\"merchantId\": null"
        );

        consumer.consume(bytes(event));

        verifyNoInteractions(recorder);
    }

    @Test
    void customerCreationEventWithNullOldStatusIsAcceptedWithoutWebhook() throws Exception {
        String event = EVENT
            .replace("\"merchantId\": \"merchant-1\"", "\"merchantId\": null")
            .replace("\"oldStatus\": \"IN_TRANSIT\"", "\"oldStatus\": null")
            .replace("\"newStatus\": \"DELIVERED\"", "\"newStatus\": \"CREATED\"");

        consumer.consume(bytes(event));

        verifyNoInteractions(recorder);
    }

    @Test
    void unknownOptionalFieldsAreIgnored() throws Exception {
        String event = EVENT.replace(
                "\"courierId\": null",
                "\"courierId\": null, \"futureField\": \"value\""
        );

        consumer.consume(bytes(event));

        verify(recorder).record(
                EVENT_ID,
                OCCURRED_AT,
                "merchant-1",
                "SD-100",
                "SHOP-100",
                "DELIVERED"
        );
    }

    @Test
    void malformedOrIncompleteEventsAreRejected() {
        String[] invalidEvents = {
                "{broken-json",
                "null",
                EVENT.replace(
                        "\"eventType\": \"ParcelStatusChanged\"",
                        "\"eventType\": \"SomethingElse\""
                ),
                EVENT.replace(
                        "\"customerId\": \"customer-1\"",
                        "\"customerId\": null"
                ),
                EVENT.replace(
                        "\"newStatus\": \"DELIVERED\"",
                        "\"newStatus\": \"\""
                ),
                EVENT.replace(
                        "\"oldStatus\": \"IN_TRANSIT\"",
                        "\"oldStatus\": null"
                )
        };

        for (String event : invalidEvents) {
            assertThrows(
                    Exception.class,
                    () -> consumer.consume(bytes(event))
            );
        }

        verifyNoInteractions(recorder);
    }

    @Test
    void databaseFailureIsPropagatedToListenerContainer() {
        var failure = new DataAccessResourceFailureException(
                "Database unavailable"
        );

        when(recorder.record(
                EVENT_ID,
                OCCURRED_AT,
                "merchant-1",
                "SD-100",
                "SHOP-100",
                "DELIVERED"
        )).thenThrow(failure);

        var thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> consumer.consume(bytes(EVENT))
        );

        assertSame(failure, thrown);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
