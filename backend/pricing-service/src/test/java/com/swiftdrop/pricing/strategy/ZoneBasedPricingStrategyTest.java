package com.swiftdrop.pricing.strategy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static com.swiftdrop.pricing.PricingFixtures.context;

class ZoneBasedPricingStrategyTest {
    @Test void calculatesKnownPrice() {
        assertThat(new ZoneBasedPricingStrategy().calculate(new BigDecimal("0"), context(true)))
                .isEqualByComparingTo("200.00");
    }
}
