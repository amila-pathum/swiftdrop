package com.swiftdrop.pricing.strategy;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;
import static com.swiftdrop.pricing.PricingFixtures.context;

class PricingCalculatorTest {
    @Test void springOrdersStrategiesRegardlessOfRegistrationOrder() {
        try (var spring = new AnnotationConfigApplicationContext(
                ExpressPricingStrategy.class, WeightBasedPricingStrategy.class,
                ZoneBasedPricingStrategy.class, PricingCalculator.class)) {
            var calculator = spring.getBean(PricingCalculator.class);
            assertThat(calculator.calculate(context(true))).isEqualByComparingTo("862.50");
            assertThat(calculator.calculate(context(false))).isEqualByComparingTo("575.00");
        }
    }
}
