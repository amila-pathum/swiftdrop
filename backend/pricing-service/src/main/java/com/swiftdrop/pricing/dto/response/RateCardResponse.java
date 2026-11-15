package com.swiftdrop.pricing.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The published rates as the admin page edits them: one row for every pair of active
 * zones (including a zone with itself). Rate fields are null for a pair that has no
 * rate yet. {@code version} and {@code publishedAt} are null before the first publish.
 */
public record RateCardResponse(
        Integer version,
        Instant publishedAt,
        String publishedBy,
        SizeSurcharges sizeSurcharges,
        List<Route> routes
) {
    public record SizeSurcharges(BigDecimal small, BigDecimal medium, BigDecimal large) {
    }

    public record Route(
            ZoneSummary origin,
            ZoneSummary destination,
            BigDecimal baseWeightKg,
            BigDecimal baseFee,
            BigDecimal additionalKgFee,
            BigDecimal expressMultiplier
    ) {
    }

    public record ZoneSummary(UUID id, String name, String code) {
    }
}
