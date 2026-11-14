package com.swiftdrop.pricing.strategy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static com.swiftdrop.pricing.PricingFixtures.context;

class WeightBasedPricingStrategyTest {
    @Test void calculatesKnownPrice() {
        assertThat(new WeightBasedPricingStrategy().calculate(new BigDecimal("200"), context(true)))
                .isEqualByComparingTo("575.00");
    }
}
