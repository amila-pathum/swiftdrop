package com.swiftdrop.pricing;

import com.swiftdrop.pricing.entity.ParcelSize;
import com.swiftdrop.pricing.entity.RateVersion;
import com.swiftdrop.pricing.entity.RouteRate;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.strategy.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Western Metro (Colombo) and Central Hills (Kandy), published as v3 with the route
 * between them at base 1 kg / LKR 350, LKR 100 per additional kg, express ×1.5, and
 * size surcharges 0 / 150 / 300.
 */
public final class PricingFixtures {
    public static final UUID WESTERN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID CENTRAL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    public static Zone zone(UUID id, String name, String code, String... localities) {
        Zone zone = new Zone();
        ReflectionTestUtils.setField(zone, "id", id);
        zone.setName(name);
        zone.setCode(code);
        zone.setLocalities(List.of(localities));
        return zone;
    }
    public static Zone western() { return zone(WESTERN_ID, "Western Metro", "WP", "Colombo", "Dehiwala"); }
    public static Zone central() { return zone(CENTRAL_ID, "Central Hills", "CP", "Kandy", "Peradeniya"); }

    public static RateVersion rates() {
        RateVersion rates = new RateVersion(3, new BigDecimal("0.00"), new BigDecimal("150.00"),
                new BigDecimal("300.00"), "admin-1");
        rates.addRoute(route());
        return rates;
    }
    public static RouteRate route() {
        return new RouteRate(central(), western(), new BigDecimal("1.000"), new BigDecimal("350.00"),
                new BigDecimal("100.00"), new BigDecimal("1.50"));
    }
    public static PricingContext context(String weight, ParcelSize size, boolean express) {
        return new PricingContext(new BigDecimal(weight), size, express, route(), rates());
    }
    public static PricingCalculator calculator() {
        return new PricingCalculator(List.of(new ZoneBasedPricingStrategy(), new WeightBasedPricingStrategy(),
                new SizeSurchargePricingStrategy(), new ExpressPricingStrategy()));
    }
}
