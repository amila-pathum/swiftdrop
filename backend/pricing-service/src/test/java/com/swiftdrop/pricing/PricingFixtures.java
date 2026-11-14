package com.swiftdrop.pricing;

import com.swiftdrop.pricing.entity.PricingRule;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.strategy.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class PricingFixtures {
    public static final UUID ZONE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static Zone zone() {
        Zone zone = new Zone();
        ReflectionTestUtils.setField(zone, "id", ZONE_ID);
        zone.setName("Kandy"); zone.setCity("Kandy"); zone.setDistrict("Kandy");
        return zone;
    }
    public static PricingRule rule() {
        PricingRule rule = new PricingRule();
        ReflectionTestUtils.setField(rule, "id", RULE_ID);
        rule.setZone(zone());
        rule.setBaseFee(new BigDecimal("200.00"));
        rule.setPricePerKg(new BigDecimal("150.00"));
        rule.setExpressMultiplier(new BigDecimal("1.50"));
        return rule;
    }
    public static PricingContext context(boolean express) {
        return new PricingContext(new BigDecimal("2.5"), rule(), express);
    }
    public static PricingCalculator calculator() {
        return new PricingCalculator(List.of(new ZoneBasedPricingStrategy(), new WeightBasedPricingStrategy(), new ExpressPricingStrategy()));
    }
}
