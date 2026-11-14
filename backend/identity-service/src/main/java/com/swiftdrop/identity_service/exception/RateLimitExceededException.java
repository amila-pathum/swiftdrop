package com.swiftdrop.identity_service.exception;

public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException() {
        super("Too many requests. Please try again later.");
    }
}
