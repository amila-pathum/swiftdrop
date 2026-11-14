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
    private final Map<String, List<ParcelLiveStatusResponse>> parcelStatusHistory = new ConcurrentHashMap<>();

    /**
     * Subscribes an authenticated user to their real-time notification stream.
     */
    public SseEmitter subscribeUser(String userId) {
        return subscribeUser(userId, null);
    }

    /**
     * Subscribes an authenticated user to their real-time notification stream by userId and/or email.
     */
    public SseEmitter subscribeUser(String userId, String email) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        List<String> identifiers = java.util.stream.Stream.of(userId, email)
                .filter(org.springframework.util.StringUtils::hasText)
                .distinct()
                .toList();

        for (String id : identifiers) {
            userEmitters.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);
        }

        Runnable cleanup = () -> {
            for (String id : identifiers) {
                List<SseEmitter> emitters = userEmitters.get(id);
                if (emitters != null) {
                    emitters.remove(emitter);
                    if (emitters.isEmpty()) {
                        userEmitters.remove(id);
                    }
                }
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

            // Replay previous live status events so newly joined or returning clients see previous updates immediately
            List<ParcelLiveStatusResponse> history = parcelStatusHistory.get(trackingNumber);
            if (history != null && !history.isEmpty()) {
                for (ParcelLiveStatusResponse item : history) {
                    emitter.send(SseEmitter.event()
                            .name("status-changed")
                            .data(item));
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data(item));
                }
            }
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
        ParcelLiveStatusResponse payload = ParcelLiveStatusResponse.builder()
                .trackingNumber(trackingNumber)
                .status(status)
                .occurredAt(occurredAt)
                .build();

        // Always store in history so newly connected or returning clients can see it
        parcelStatusHistory.computeIfAbsent(trackingNumber, k -> new CopyOnWriteArrayList<>()).add(payload);

        List<SseEmitter> emitters = parcelEmitters.get(trackingNumber);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("status-changed")
                        .data(payload));
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(payload));
            } catch (IOException e) {
                log.debug("Removing failed emitter for tracking number {}", trackingNumber);
                emitters.remove(emitter);
            }
        }
    }
}

