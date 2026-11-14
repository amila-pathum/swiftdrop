package com.swiftdrop.notification.service;

import com.swiftdrop.notification.dto.response.NotificationResponse;
import com.swiftdrop.notification.dto.response.ParcelLiveStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class NotificationStreamService {

    // 30 minutes timeout for SSE connections
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final Map<String, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();
    private final Map<String, List<SseEmitter>> parcelEmitters = new ConcurrentHashMap<>();

    /**
     * Subscribes an authenticated user to their real-time notification stream.
     */
    public SseEmitter subscribeUser(String userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        List<SseEmitter> emitters = userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        Runnable cleanup = () -> {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to user notification stream"));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE handshake to user {}", userId, e);
            cleanup.run();
        }

        return emitter;
    }

    /**
     * Subscribes a client to live status updates for a specific parcel tracking number.
     * Publicly accessible, strictly sending status and time only.
     */
    public SseEmitter subscribeParcel(String trackingNumber) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        List<SseEmitter> emitters = parcelEmitters.computeIfAbsent(trackingNumber, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        Runnable cleanup = () -> {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                parcelEmitters.remove(trackingNumber);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to parcel tracking stream"));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE handshake for tracking number {}", trackingNumber, e);
            cleanup.run();
        }

        return emitter;
    }

    /**
     * Broadcasts a notification to all active SSE connections for the given user.
     */
    public void broadcastToUser(String userId, NotificationResponse notification) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException e) {
                log.debug("Removing failed emitter for user {}", userId);
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Broadcasts live status and timestamp only for a tracking number (safe for public tracking).
     */
    public void broadcastParcelStatus(String trackingNumber, String status, Instant occurredAt) {
        List<SseEmitter> emitters = parcelEmitters.get(trackingNumber);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        ParcelLiveStatusResponse payload = ParcelLiveStatusResponse.builder()
                .trackingNumber(trackingNumber)
                .status(status)
                .occurredAt(occurredAt)
                .build();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status-changed")
                        .data(payload));
            } catch (IOException e) {
                log.debug("Removing failed emitter for tracking number {}", trackingNumber);
                emitters.remove(emitter);
            }
        }
    }
}

