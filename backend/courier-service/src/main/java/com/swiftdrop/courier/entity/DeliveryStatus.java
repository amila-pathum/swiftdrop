package com.swiftdrop.courier.entity;
public enum DeliveryStatus {
    PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, FAILED;
    public boolean terminal() { return this == DELIVERED || this == FAILED; }
}
