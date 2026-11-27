package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.dto.request.ZoneRequest;
import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.exception.PricingConflictException;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import com.swiftdrop.pricing.repository.RouteRateRepository;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final RouteRateRepository routeRateRepository;

    public ZoneService(ZoneRepository zoneRepository, RouteRateRepository routeRateRepository) {
        this.zoneRepository = zoneRepository;
        this.routeRateRepository = routeRateRepository;
    }

    @Transactional(readOnly = true)
    public List<Zone> getAllZones() {
        return zoneRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<Zone> getActiveZones() {
        return zoneRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Zone getZoneById(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ZoneNotFoundException(id));
    }

    /** The active zone covering a locality (the city a customer picks). */
    @Transactional(readOnly = true)
    public Zone getZoneByLocality(String locality) {
        if (locality == null || locality.isBlank()) {
            throw new IllegalArgumentException("City is required");
        }
        return zoneRepository.findByLocality(locality.trim())
                .filter(Zone::isActive)
                .orElseThrow(() -> new ZoneNotFoundException(locality.trim()));
    }

    public Zone createZone(ZoneRequest request) {
        Zone zone = new Zone();
        apply(zone, request);
        return zoneRepository.save(zone);
    }

    public Zone updateZone(UUID id, ZoneRequest request) {
        Zone zone = getZoneById(id);
        apply(zone, request);
        return zoneRepository.save(zone);
    }

    public Zone disableZone(UUID id) {
        Zone zone = getZoneById(id);
        zone.setActive(false);
        return zoneRepository.save(zone);
    }

    /**
     * Deletes a zone that no rate card has ever priced. Rate card versions are kept
     * as history, so a zone they use can only be disabled.
     */
    public void deleteZone(UUID id) {
        Zone zone = getZoneById(id);
        if (routeRateRepository.existsForZone(id)) {
            throw new PricingConflictException(zone.getName()
                + " is used in a rate card, so it can't be deleted. Disable it instead to stop new bookings.");
        }
        zoneRepository.delete(zone);
        zoneRepository.flush();
    }

    public Zone enableZone(UUID id) {
        Zone zone = getZoneById(id);
        zone.setActive(true);
        return zoneRepository.save(zone);
    }

    private void apply(Zone zone, ZoneRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        zoneRepository.findByCodeIgnoreCase(code)
                .filter(other -> !other.getId().equals(zone.getId()))
                .ifPresent(other -> {
                    throw new PricingConflictException(
                            "Zone code " + code + " is already used by " + other.getName());
                });

        List<String> localities = normalize(request.localities());
        for (String locality : localities) {
            zoneRepository.findByLocality(locality)
                    .filter(other -> !other.getId().equals(zone.getId()))
                    .ifPresent(other -> {
                        throw new PricingConflictException(
                                locality + " is already covered by " + other.getName());
                    });
        }

        if (zone.getId() != null && !zone.getLocalities().equals(localities)) {
            // Hibernate rewrites list rows position by position, so reordering in place
            // could briefly give two rows the same locality; remove the old rows first.
            zone.getLocalities().clear();
            zoneRepository.flush();
        }

        zone.setName(request.name().trim());
        zone.setCode(code);
        zone.setLocalities(localities);
        if (request.active() != null) {
            zone.setActive(request.active());
        }
    }

    /** Trims, drops blanks and repeats (ignoring case), and keeps the admin's order. */
    static List<String> normalize(List<String> localities) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : localities) {
            String locality = value == null ? "" : value.trim().replaceAll("\\s+", " ");
            if (!locality.isEmpty() && seen.add(locality.toLowerCase(Locale.ROOT))) {
                result.add(locality);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Add at least one covered locality");
        }
        return result;
    }
}
