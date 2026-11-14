package com.swiftdrop.pricing.exception;

/** No published rate applies, e.g. nothing is published yet or a route has no rate. */
public class RateNotFoundException extends RuntimeException {
    public RateNotFoundException(String message) { super(message); }
}
