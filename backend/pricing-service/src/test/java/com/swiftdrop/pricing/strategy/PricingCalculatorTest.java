package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ParcelSize;
import com.swiftdrop.pricing.entity.RateVersion;
import com.swiftdrop.pricing.entity.RouteRate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static com.swiftdrop.pricing.PricingFixtures.*;

class PricingCalculatorTest {

    // The screenshot's example: Colombo → Kandy, 2 kg, Small, standard = 350 + 100 + 0.
    @Test void combinesStrategiesForAStandardParcel() {
        var price = calculator().calculate(context("2", ParcelSize.SMALL, false));
        assertThat(price.amount()).isEqualByComparingTo("450.00");
        assertThat(price.breakdown()).containsExactly(
                entry("baseFee", "350.00"), entry("weightFee", "100.00"),
                entry("sizeFee", "0.00"), entry("expressFee", "0.00"));
    }

    // 2.3 kg → 2 additional kg; (350 + 200 + 150) × 1.5 = 1050.
    @Test void expressAppliesTheMultiplierToEverythingBeforeIt() {
        var price = calculator().calculate(context("2.3", ParcelSize.MEDIUM, true));
        assertThat(price.amount()).isEqualByComparingTo("1050.00");
        assertThat(price.breakdown()).containsExactly(
                entry("baseFee", "350.00"), entry("weightFee", "200.00"),
                entry("sizeFee", "150.00"), entry("expressFee", "350.00"));
    }

    @Test void roundsEachStepSoTheBreakdownAddsUpToTheTotal() {
        RouteRate odd = new RouteRate(central(), western(), new BigDecimal("0.000"), new BigDecimal("10.05"),
                new BigDecimal("7.77"), new BigDecimal("1.15"));
        RateVersion rates = new RateVersion(1, new BigDecimal("0.33"), BigDecimal.ZERO, BigDecimal.ZERO, "admin");
        var price = calculator().calculate(new PricingContext(new BigDecimal("0.333"), ParcelSize.SMALL, true, odd, rates));
        // 10.05 + 7.77 + 0.33 = 18.15; × 1.15 = 20.8725 → 20.87
        assertThat(price.amount()).isEqualTo(new BigDecimal("20.87"));
        assertThat(price.breakdown().values()).allSatisfy(value -> assertThat(value.scale()).isEqualTo(2));
        assertThat(price.breakdown().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualTo(price.amount());
    }

    @Test void springOrdersStrategiesRegardlessOfRegistrationOrder() {
        try (var spring = new AnnotationConfigApplicationContext(
                ExpressPricingStrategy.class, SizeSurchargePricingStrategy.class, WeightBasedPricingStrategy.class,
                ZoneBasedPricingStrategy.class, PricingCalculator.class)) {
            var price = spring.getBean(PricingCalculator.class).calculate(context("2.3", ParcelSize.MEDIUM, true));
            assertThat(price.amount()).isEqualByComparingTo("1050.00");
            assertThat(List.copyOf(price.breakdown().keySet()))
                    .containsExactly("baseFee", "weightFee", "sizeFee", "expressFee");
        }
    }

    // A rule added later is a new class; the calculator and other strategies are untouched.
    @Test void aNewStrategyJoinsTheBreakdownWithoutChangingOthers() {
        PricingStrategy fuel = new PricingStrategy() {
            public String breakdownKey() { return "fuelFee"; }
            public BigDecimal calculate(BigDecimal amount, PricingContext context) { return amount.add(new BigDecimal("25")); }
        };
        var calculator = new PricingCalculator(List.of(new ZoneBasedPricingStrategy(), fuel));
        var price = calculator.calculate(context("1", ParcelSize.SMALL, false));
        assertThat(price.amount()).isEqualByComparingTo("375.00");
        assertThat(price.breakdown()).containsEntry("fuelFee", new BigDecimal("25.00"));
    }

    private static java.util.Map.Entry<String, BigDecimal> entry(String key, String value) {
        return java.util.Map.entry(key, new BigDecimal(value));
    }
}
