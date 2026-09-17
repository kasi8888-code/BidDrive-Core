package com.biddrive.core.services;

import com.biddrive.core.dtos.NearbyDriverDto;
import com.biddrive.core.models.Driver;
import com.biddrive.core.repositories.DriverRepository;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DriverService {

    private final DriverRepository driverRepository;
    private final DriverLocationRedisService driverLocationRedisService;

    public DriverService(DriverRepository driverRepository,
                         DriverLocationRedisService driverLocationRedisService) {
        this.driverRepository = driverRepository;
        this.driverLocationRedisService = driverLocationRedisService;
    }

    public List<Driver> getAllDrivers() {
        return driverRepository.findAll();
    }

    public Optional<Driver> getDriverById(Integer id) {
        return driverRepository.findById(id);
    }

    public Driver createDriver(Driver driver) {
        return driverRepository.save(driver);
    }

    public Driver updateDriver(Driver driver) {
        return driverRepository.save(driver);
    }

    public void deleteDriver(Integer id) {
        driverLocationRedisService.removeDriverLocation(id);
        driverRepository.deleteById(id);
    }

    /**
     * Ingests driver real-time GPS coordinates.
     * Updates Redis Geo set (for ultra-fast proximity queries) and PostgreSQL (for durability).
     */
    public Driver updateDriverLocation(Integer driverId, Double latitude, Double longitude) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));

        driver.setCurrentLatitude(latitude);
        driver.setCurrentLongitude(longitude);
        driver.setLastLocationUpdate(LocalDateTime.now());
        Driver savedDriver = driverRepository.save(driver);

        // Update high-speed Redis in-memory spatial index
        // Update high-speed Redis in-memory spatial index (only if Available)
        if ("Available".equalsIgnoreCase(savedDriver.getStatus())) {
            driverLocationRedisService.updateDriverLocation(driverId, latitude, longitude);
        }

        return savedDriver;
    }

    /**
     * Updates driver online/duty status (e.g., 'Available', 'BUSY', 'Offline').
     * If going offline or busy, cleans up the driver from the active Redis geospatial index.
     */
    public Driver updateDriverStatus(Integer driverId, String status) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));

        driver.setStatus(status);
        Driver savedDriver = driverRepository.save(driver);

        if (!"Available".equalsIgnoreCase(status)) {
            driverLocationRedisService.removeDriverLocation(driverId);
        } else if (driver.getCurrentLatitude() != null && driver.getCurrentLongitude() != null) {
            driverLocationRedisService.updateDriverLocation(driverId, driver.getCurrentLatitude(), driver.getCurrentLongitude());
        }

        return savedDriver;
    }

    /**
     * Searches for nearby drivers within radiusKm of the target coordinates.
     * Combines Redis Geo distance results with driver profile details.
     * CRITICAL: Only drivers who are currently 'Available' are returned.
     */
    public List<NearbyDriverDto> findNearbyDrivers(Double latitude, Double longitude, Double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults =
                driverLocationRedisService.findNearbyDrivers(latitude, longitude, radiusKm);

        List<NearbyDriverDto> nearbyDrivers = new ArrayList<>();
        if (geoResults == null) {
            return nearbyDrivers;
        }

        for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : geoResults) {
            String memberName = String.valueOf(result.getContent().getName());
            try {
                Integer driverId = Integer.parseInt(memberName);
                Optional<Driver> driverOpt = driverRepository.findById(driverId);
                if (driverOpt.isPresent()) {
                    Driver d = driverOpt.get();

                    // FILTER: Only take drivers whose status is currently 'Available'
                    if (!"Available".equalsIgnoreCase(d.getStatus())) {
                        continue;
                    }

                    double rawDistance = result.getDistance().getValue();
                    double roundedDistance = BigDecimal.valueOf(rawDistance)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();

                    NearbyDriverDto dto = NearbyDriverDto.builder()
                            .driverId(d.getId())
                            .name(d.getName())
                            .email(d.getEmail())
                            .carType(d.getCarType())
                            .licensePlate(d.getLicensePlate())
                            .rating(d.getRating())
                            .latitude(d.getCurrentLatitude())
                            .longitude(d.getCurrentLongitude())
                            .distanceKm(roundedDistance)
                            .build();

                    nearbyDrivers.add(dto);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return nearbyDrivers;
    }
}