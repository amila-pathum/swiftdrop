package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.request.PublishRatesRequest;
import com.swiftdrop.pricing.dto.response.RateCardResponse;
import com.swiftdrop.pricing.dto.response.RateVersionSummary;
import com.swiftdrop.pricing.entity.RateVersion;
import com.swiftdrop.pricing.entity.RouteRate;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.exception.PricingConflictException;
import com.swiftdrop.pricing.exception.RateNotFoundException;
import com.swiftdrop.pricing.repository.RateVersionRepository;
import com.swiftdrop.pricing.repository.RouteRateRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Published rate versions: reading the current card, publishing, and route lookup. */
@Service
@Transactional
public class RateCardService {

    private final RateVersionRepository versions;
    private final RouteRateRepository routes;
    private final ZoneRepository zones;

    public RateCardService(RateVersionRepository versions, RouteRateRepository routes,
            ZoneRepository zones) {
        this.versions = versions;
        this.routes = routes;
        this.zones = zones;
    }

    @Transactional(readOnly = true)
    public Optional<RateVersion> currentVersion() {
        return versions.findTopByOrderByVersionDesc();
    }

    /** The current version's rate between two zones, in either direction. */
    @Transactional(readOnly = true)
    public RouteRate getRoute(RateVersion version, Zone first, Zone second) {
        return routes.findRoute(version.getId(), first.getId(), second.getId())
                .orElseThrow(() -> new RateNotFoundException(first.getId().equals(second.getId())
                        ? "No published rate within " + first.getName()
                        : "No published rate between " + first.getName() + " and " + second.getName()));
    }

    /** Every pair of active zones, filled in from the current version where it has a rate. */
    @Transactional(readOnly = true)
    public RateCardResponse getRateCard() {
        Optional<RateVersion> current = currentVersion();
        List<RouteRate> published = current.map(RateVersion::getRoutes).orElse(List.of());
        List<Zone> active = zones.findByActiveTrueOrderByNameAsc();

        List<RateCardResponse.Route> rows = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i; j < active.size(); j++) {
                Zone origin = active.get(i);
                Zone destination = active.get(j);
                RouteRate rate = published.stream()
                        .filter(route -> route.connects(origin.getId(), destination.getId()))
                        .findFirst().orElse(null);
                rows.add(new RateCardResponse.Route(summary(origin), summary(destination),
                        rate == null ? null : rate.getBaseWeightKg(),
                        rate == null ? null : rate.getBaseFee(),
                        rate == null ? null : rate.getAdditionalKgFee(),
                        rate == null ? null : rate.getExpressMultiplier()));
            }
        }

        RateCardResponse.SizeSurcharges surcharges = current
                .map(v -> new RateCardResponse.SizeSurcharges(
                        v.getSmallSurcharge(), v.getMediumSurcharge(), v.getLargeSurcharge()))
                .orElse(new RateCardResponse.SizeSurcharges(
                        BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)));

        return new RateCardResponse(
                current.map(RateVersion::getVersion).orElse(null),
                current.map(RateVersion::getPublishedAt).orElse(null),
                current.map(RateVersion::getPublishedBy).orElse(null),
                surcharges, rows);
    }

    @Transactional(readOnly = true)
    public List<RateVersionSummary> getVersionHistory() {
        return versions.findAllByOrderByVersionDesc().stream()
                .map(v -> new RateVersionSummary(v.getVersion(), v.getPublishedAt(),
                        v.getPublishedBy(), routes.countByVersion(v.getId())))
                .toList();
    }

    /**
     * Publishes the next version. Rates for pairs involving a disabled zone are carried
     * over from the current version (the admin page cannot show them), so re-enabling a
     * zone restores its prices.
     */
    public RateCardResponse publish(PublishRatesRequest request, String publisher) {
        Optional<RateVersion> current = currentVersion();
        Integer currentNumber = current.map(RateVersion::getVersion).orElse(null);
        if (!Objects.equals(currentNumber, request.basedOnVersion())) {
            throw new PricingConflictException(currentNumber == null
                    ? "The rates you were editing no longer exist. Reload and try again."
                    : "Version v" + currentNumber + " was published while you were editing. "
                        + "Reload to see it, then make your changes again.");
        }

        Map<UUID, Zone> byId = zones.findAll().stream()
                .collect(Collectors.toMap(Zone::getId, Function.identity()));

        RateVersion next = new RateVersion(currentNumber == null ? 1 : currentNumber + 1,
                request.sizeSurcharges().small(), request.sizeSurcharges().medium(),
                request.sizeSurcharges().large(), publisher);

        Set<String> pairs = new HashSet<>();
        for (PublishRatesRequest.Route route : request.routes()) {
            Zone origin = activeZone(byId, route.originZoneId());
            Zone destination = activeZone(byId, route.destinationZoneId());
            if (!pairs.add(pairKey(origin.getId(), destination.getId()))) {
                throw new IllegalArgumentException("The route " + origin.getName() + " – "
                        + destination.getName() + " is listed more than once");
            }
            next.addRoute(new RouteRate(origin, destination, route.baseWeightKg(),
                    route.baseFee(), route.additionalKgFee(), route.expressMultiplier()));
        }

        current.ifPresent(previous -> previous.getRoutes().stream()
                .filter(route -> !route.getZoneA().isActive() || !route.getZoneB().isActive())
                .filter(route -> pairs.add(pairKey(route.getZoneA().getId(), route.getZoneB().getId())))
                .forEach(route -> next.addRoute(route.copy())));

        versions.saveAndFlush(next);
        return getRateCard();
    }

    private static Zone activeZone(Map<UUID, Zone> byId, UUID id) {
        Zone zone = byId.get(id);
        if (zone == null || !zone.isActive()) {
            throw new IllegalArgumentException("Zone " + id + " does not exist or is disabled");
        }
        return zone;
    }

    private static String pairKey(UUID first, UUID second) {
        return RouteRate.compare(first, second) <= 0 ? first + ":" + second : second + ":" + first;
    }

    private static RateCardResponse.ZoneSummary summary(Zone zone) {
        return new RateCardResponse.ZoneSummary(zone.getId(), zone.getName(), zone.getCode());
    }
}
