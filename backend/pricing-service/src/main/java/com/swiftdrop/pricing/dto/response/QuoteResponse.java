package com.swiftdrop.pricing.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record QuoteResponse(

        BigDecimal charge,

        String currency,

        Map<String, BigDecimal> breakdown

) {
}