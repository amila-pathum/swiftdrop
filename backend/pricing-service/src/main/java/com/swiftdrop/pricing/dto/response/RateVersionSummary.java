package com.swiftdrop.pricing.dto.response;

import java.time.Instant;

public record RateVersionSummary(int version, Instant publishedAt, String publishedBy, long routeCount) {
}
