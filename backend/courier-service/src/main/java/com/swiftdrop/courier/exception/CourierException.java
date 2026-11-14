package com.swiftdrop.courier.exception;
import org.springframework.http.HttpStatus;

public class CourierException extends RuntimeException {
    public final HttpStatus status;
    public CourierException(HttpStatus status, String message) { super(message); this.status=status; }
    public static CourierException missing(String message) { return new CourierException(HttpStatus.NOT_FOUND,message); }
    public static CourierException invalid(String message) { return new CourierException(HttpStatus.BAD_REQUEST,message); }
    public static CourierException conflict(String message) { return new CourierException(HttpStatus.CONFLICT,message); }
    public static CourierException forbidden(String message) { return new CourierException(HttpStatus.FORBIDDEN,message); }
}
