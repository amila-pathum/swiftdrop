package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ParcelSize;
import com.swiftdrop.pricing.entity.RateVersion;
import com.swiftdrop.pricing.entity.RouteRate;
import java.math.BigDecimal;

/**
 * Everything a strategy may price on: the parcel, the rate for its route, and the
 * version-wide settings such as size surcharges.
 */
public record PricingContext(
        BigDecimal weight,
        ParcelSize size,
        boolean express,
        RouteRate route,
        RateVersion rates
) {
}
