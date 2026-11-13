package com.swiftdrop.dispatch.exception;

import org.springframework.http.HttpStatus;

public class DispatchException extends RuntimeException {
    private final HttpStatus status;

    public DispatchException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
