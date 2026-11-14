package com.swiftdrop.pricing.strategy;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static com.swiftdrop.pricing.PricingFixtures.context;

class ExpressPricingStrategyTest {
    @Test void calculatesKnownPrice() {
        assertThat(new ExpressPricingStrategy().calculate(new BigDecimal("575"), context(true)))
                .isEqualByComparingTo("862.50");
    }

    @Test void standardDoesNotApplyMultiplier() {
        assertThat(new ExpressPricingStrategy().calculate(new BigDecimal("575"), context(false)))
                .isEqualByComparingTo("575");
    }
}
