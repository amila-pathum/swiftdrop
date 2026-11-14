package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.springframework.stereotype.Service;
import com.swiftdrop.pricing.exception.ZoneNotFoundException;
import com.swiftdrop.pricing.exception.PricingConflictException;

import java.util.List;
import java.util.UUID;

@Service
@org.springframework.transaction.annotation.Transactional
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    public List<Zone> getAllZones() {
        return zoneRepository.findAll();
    }

    public List<Zone> getActiveZones() {
        return zoneRepository.findByActiveTrue();
    }

    public Zone getZoneById(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() ->
                        new ZoneNotFoundException(id));
    }

    public Zone getZoneByCity(String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City is required");
        }

        return zoneRepository.findByCityIgnoreCase(city.trim())
                .filter(Zone::isActive)
                .orElseThrow(() ->
                        new ZoneNotFoundException(city.trim()));
    }

    public Zone createZone(String name, String city, String district) {
        city = city.trim();
        if (zoneRepository.existsByCityIgnoreCase(city)) {
            throw new PricingConflictException(
                    "A zone already exists for this city");
        }

        Zone zone = new Zone();
        zone.setName(name.trim());
        zone.setCity(city);
        zone.setDistrict(district.trim());

        return zoneRepository.save(zone);
    }

    public Zone updateZone(UUID id, String name, String city, String district) {
        city = city.trim();
        Zone existingZone = getZoneById(id);

        if (!existingZone.getCity().equalsIgnoreCase(city)
                && zoneRepository.existsByCityIgnoreCase(city)) {
            throw new PricingConflictException(
                    "A zone already exists for this city");
        }

        existingZone.setName(name.trim());
        existingZone.setCity(city);
        existingZone.setDistrict(district.trim());

        return zoneRepository.save(existingZone);
    }

    public Zone disableZone(UUID id) {
        Zone zone = getZoneById(id);
        zone.setActive(false);
        return zoneRepository.save(zone);
    }
    public Zone enableZone(UUID id) {
        Zone zone = getZoneById(id);
        zone.setActive(true);
        return zoneRepository.save(zone);
    }
}