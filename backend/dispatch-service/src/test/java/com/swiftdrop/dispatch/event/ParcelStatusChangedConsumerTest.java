package com.swiftdrop.dispatch.event;

import com.swiftdrop.dispatch.exception.DispatchException;
import com.swiftdrop.dispatch.service.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParcelStatusChangedConsumerTest {
    private final DispatchService dispatch = mock(DispatchService.class);
    private final ParcelStatusChangedConsumer consumer =
        new ParcelStatusChangedConsumer(JsonMapper.builder().findAndAddModules().build(), dispatch);

    private static final String VALID = """
        {"eventId":"7d0f8e2c-1111-4a5b-9c3d-000000000001","eventType":"ParcelStatusChanged",
         "occurredAt":"2026-11-01T10:00:00Z","trackingNumber":"SD-1",
         "parcelId":"20000000-0000-4000-8000-000000000001","merchantId":null,
         "merchantOrderReference":null,"customerId":"5","courierId":"70",
         "oldStatus":"OUT_FOR_DELIVERY","newStatus":"DELIVERED"}
        """;

    @Test
    void storesAValidEvent() {
        when(dispatch.applyStatusEvent(any())).thenReturn(true);

        consumer.consume(VALID.getBytes(StandardCharsets.UTF_8));

        verify(dispatch).applyStatusEvent(any());
    }

    @Test
    void malformedMessageIsDeadLetteredNotRequeued() {
        assertThatThrownBy(() -> consumer.consume("not json".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(dispatch, never()).applyStatusEvent(any());
    }

    @Test
    void invalidEventIsDeadLetteredNotRequeued() {
        when(dispatch.applyStatusEvent(any()))
            .thenThrow(new DispatchException(HttpStatus.BAD_REQUEST, "Invalid parcel status event"));

        assertThatThrownBy(() -> consumer.consume(VALID.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void otherFailuresAreLeftToTheRetryPolicy() {
        when(dispatch.applyStatusEvent(any()))
            .thenThrow(new DispatchException(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        assertThatThrownBy(() -> consumer.consume(VALID.getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(DispatchException.class);
    }
}
