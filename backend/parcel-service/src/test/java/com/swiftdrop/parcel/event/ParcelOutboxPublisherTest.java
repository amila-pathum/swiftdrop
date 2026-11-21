package com.swiftdrop.parcel.event;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParcelOutboxPublisherTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    final UUID eventId = UUID.randomUUID();
    final ParcelOutboxPublisher publisher = new ParcelOutboxPublisher(jdbc, rabbit, 20);

    @BeforeEach void setup() throws Exception {
        var row = mock(ResultSet.class);
        when(row.getObject("event_id", UUID.class)).thenReturn(eventId);
        when(row.getString("routing_key")).thenReturn("parcel.status-changed");
        when(row.getString("payload")).thenReturn("{\"eventId\":\"" + eventId + "\"}");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(inv ->
            List.of(((RowMapper<?>) inv.getArgument(1)).mapRow(row, 0)));
    }

    @Test void ackMarksPublishedAndSendsPersistentJsonWithStableId() {
        doAnswer(inv -> {
            Message message = inv.getArgument(2);
            CorrelationData correlation = inv.getArgument(3);
            assertEquals(eventId.toString(), message.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
            verify(jdbc, never()).update(contains("SET published_at"), any(), any());
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq("swiftdrop.events"), eq("parcel.status-changed"), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        verify(jdbc).update(contains("SET published_at"), any(java.sql.Timestamp.class), eq(eventId));
        verify(jdbc, never()).update(contains("next_attempt_at ="), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"nack", "returned", "timeout", "exception", "connection"})
    void failedDeliveryRemainsPending(String failure) {
        doAnswer(inv -> {
            CorrelationData correlation = inv.getArgument(3);
            switch (failure) {
                case "nack" -> correlation.getFuture().complete(new CorrelationData.Confirm(false, "test nack"));
                case "returned" -> {
                    correlation.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", "swiftdrop.events", "parcel.status-changed"));
                    correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
                }
                case "exception" -> correlation.getFuture().completeExceptionally(new IllegalStateException("closed"));
                case "connection" -> throw new org.springframework.amqp.AmqpException("connection lost");
                default -> { }
            }
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        verify(jdbc, never()).update(contains("SET published_at"), any(), any());
        verify(jdbc).update(contains("next_attempt_at ="), anyString(), eq(eventId));
    }

    @Test void lateAckDoesNotMarkTimedOutAttemptPublishedAndRetryKeepsEventId() {
        var attempts = new java.util.ArrayList<CorrelationData>();
        doAnswer(inv -> {
            CorrelationData correlation = inv.getArgument(3);
            attempts.add(correlation);
            if (attempts.size() == 2) {
                correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishPending();
        attempts.getFirst().getFuture().complete(new CorrelationData.Confirm(true, null));
        verify(jdbc, never()).update(contains("SET published_at"), any(), any());
        publisher.publishPending();
        assertEquals(attempts.getFirst().getId(), attempts.getLast().getId());
        assertNotSame(attempts.getFirst(), attempts.getLast());
        verify(jdbc).update(contains("SET published_at"), any(java.sql.Timestamp.class), eq(eventId));
    }

    @Test void interruptionPreservesInterruptFlagAndPendingEvent() {
        Thread.currentThread().interrupt();
        try {
            publisher.publishPending();
            assertTrue(Thread.currentThread().isInterrupted());
            verify(jdbc).update(contains("next_attempt_at ="), contains("Interrupted"), eq(eventId));
            verify(jdbc, never()).update(contains("SET published_at"), any(), any());
        } finally {
            Thread.interrupted();
        }
    }
}
