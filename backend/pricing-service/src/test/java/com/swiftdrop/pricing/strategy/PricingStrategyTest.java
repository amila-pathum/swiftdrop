package com.swiftdrop.pricing.strategy;

import com.swiftdrop.pricing.entity.ParcelSize;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
import static com.swiftdrop.pricing.PricingFixtures.context;

/** Each strategy on its own, with the fixed route from PricingFixtures. */
class PricingStrategyTest {
    private static final BigDecimal RUNNING = new BigDecimal("100.00");

    @Test void zoneBasedAddsTheRouteBaseFee() {
        assertThat(new ZoneBasedPricingStrategy().calculate(RUNNING, context("2", ParcelSize.SMALL, false)))
                .isEqualByComparingTo("450.00");
    }

    // Base weight 1 kg, LKR 100 per additional kg, charged per started kg.
    @ParameterizedTest
    @CsvSource({"0.5, 0", "1, 0", "1.001, 100", "2, 100", "2.3, 200", "3, 200"})
    void weightBasedChargesEachStartedKgAboveTheBaseWeight(String weight, String added) {
        assertThat(new WeightBasedPricingStrategy().calculate(RUNNING, context(weight, ParcelSize.SMALL, false))
                .subtract(RUNNING)).isEqualByComparingTo(added);
    }

    @ParameterizedTest
    @CsvSource({"SMALL, 0", "MEDIUM, 150", "LARGE, 300"})
    void sizeSurchargeAddsTheBandsFee(ParcelSize size, String added) {
        assertThat(new SizeSurchargePricingStrategy().calculate(RUNNING, context("1", size, false))
                .subtract(RUNNING)).isEqualByComparingTo(added);
    }

    @Test void expressMultipliesTheRunningAmount() {
        assertThat(new ExpressPricingStrategy().calculate(new BigDecimal("700"), context("1", ParcelSize.SMALL, true)))
                .isEqualByComparingTo("1050.00");
    }

    @Test void standardLeavesTheAmountUnchanged() {
        assertThat(new ExpressPricingStrategy().calculate(new BigDecimal("700"), context("1", ParcelSize.SMALL, false)))
                .isEqualByComparingTo("700");
    }
}
