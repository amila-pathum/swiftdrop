package com.swiftdrop.identity_service.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Instance-local, in-memory rate limiter using a fixed window. Not distributed:
 * in a multi-instance deployment each instance allows its own window. Adequate
 * for a single-instance university project.
 */
@Component
public class RateLimiter {

    private final int maxAttempts;
    private final long windowMs;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(
            @Value("${swiftdrop.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${swiftdrop.rate-limit.window-seconds:900}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMs = Duration.ofSeconds(windowSeconds).toMillis();
    }

    public boolean isAllowed(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.start > windowMs) {
                return new Window(now);
            }
            return existing;
        });
        return window.counter.incrementAndGet() <= maxAttempts;
    }

    public void evictExpired() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start > windowMs);
    }

    private static final class Window {
        final long start;
        final AtomicInteger counter;

        Window(long start) {
            this.start = start;
            this.counter = new AtomicInteger(0);
        }
    }
}
