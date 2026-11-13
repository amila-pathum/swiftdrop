package com.swiftdrop.pricing.service;

import com.swiftdrop.pricing.entity.Zone;
import com.swiftdrop.pricing.repository.ZoneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    public List<Zone> getActiveZones() {
        return zoneRepository.findByActiveTrue();
    }

    public Zone getZoneById(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Zone not found"));
    }

    public Zone getZoneByCity(String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("City is required");
        }

        return zoneRepository.findByCityIgnoreCase(city.trim())
                .filter(Zone::isActive)
                .orElseThrow(() ->
                        new RuntimeException("No active zone found for city: " + city));
    }

    public Zone createZone(String name, String city, String district) {
        if (zoneRepository.existsByCityIgnoreCase(city)) {
            throw new IllegalArgumentException(
                    "A zone already exists for this city");
        }

        Zone zone = new Zone();
        zone.setName(name);
        zone.setCity(city);
        zone.setDistrict(district);

        return zoneRepository.save(zone);
    }

    public Zone updateZone(UUID id, String name, String city, String district) {
        Zone existingZone = getZoneById(id);

        if (!existingZone.getCity().equalsIgnoreCase(city)
                && zoneRepository.existsByCityIgnoreCase(city)) {
            throw new IllegalArgumentException(
                    "A zone already exists for this city");
        }

        existingZone.setName(name);
        existingZone.setCity(city);
        existingZone.setDistrict(district);

        return zoneRepository.save(existingZone);
    }

    public Zone disableZone(UUID id) {
        Zone zone = getZoneById(id);
        zone.setActive(false);
        return zoneRepository.save(zone);
    }
}