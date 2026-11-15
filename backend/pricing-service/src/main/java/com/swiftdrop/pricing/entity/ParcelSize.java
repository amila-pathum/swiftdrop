package com.swiftdrop.pricing.entity;

import java.util.Locale;

/** Parcel size bands, by longest side: Small ≤ 30 cm, Medium ≤ 60 cm, Large > 60 cm. */
public enum ParcelSize {
    SMALL, MEDIUM, LARGE;

    public static ParcelSize parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Reported below with the accepted values.
            }
        }
        throw new IllegalArgumentException("Size must be SMALL, MEDIUM or LARGE");
    }
}
