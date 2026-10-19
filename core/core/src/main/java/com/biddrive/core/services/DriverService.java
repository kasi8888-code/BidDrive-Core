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
    private final GeoDistanceService geoDistanceService;

    public DriverService(DriverRepository driverRepository,
                         DriverLocationRedisService driverLocationRedisService,
                         GeoDistanceService geoDistanceService) {
        this.driverRepository = driverRepository;
        this.driverLocationRedisService = driverLocationRedisService;
        this.geoDistanceService = geoDistanceService;
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

    private final java.util.Map<Integer, String> driverStatusCache = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isDriverAvailable(Integer driverId) {
        String dbStatus = driverRepository.findById(driverId)
                .map(Driver::getStatus).orElse("Available");
        driverStatusCache.put(driverId, dbStatus);
        return "Available".equalsIgnoreCase(dbStatus);
    }

    public Driver updateDriverLocation(Integer driverId, Double latitude, Double longitude) {
        String status = driverStatusCache.get(driverId);
        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver != null) {
            status = driver.getStatus() != null ? driver.getStatus() : "Available";
            driver.setCurrentLatitude(latitude);
            driver.setCurrentLongitude(longitude);
            driver.setLastLocationUpdate(LocalDateTime.now());
            driverRepository.save(driver);
        } else {
            if (status == null) status = "Available";
            driver = new Driver();
            driver.setId(driverId);
            driver.setStatus(status);
            driver.setCurrentLatitude(latitude);
            driver.setCurrentLongitude(longitude);
            driver.setLastLocationUpdate(LocalDateTime.now());
        }
        driverStatusCache.put(driverId, status);

        if ("Available".equalsIgnoreCase(status)) {
            driverLocationRedisService.updateDriverLocation(driverId, latitude, longitude);
        }

        return driver;
    }

    public Driver updateDriverStatus(Integer driverId, String status) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + driverId));

        driver.setStatus(status);
        Driver savedDriver = driverRepository.save(driver);
        driverStatusCache.put(driverId, status);

        if (!"Available".equalsIgnoreCase(status)) {
            driverLocationRedisService.removeDriverLocation(driverId);
        } else if (driver.getCurrentLatitude() != null && driver.getCurrentLongitude() != null) {
            driverLocationRedisService.updateDriverLocation(driverId, driver.getCurrentLatitude(), driver.getCurrentLongitude());
        }

        return savedDriver;
    }

    public List<NearbyDriverDto> findNearbyDrivers(Double latitude, Double longitude, Double radiusKm) {
        GeoResults<RedisGeoCommands.GeoLocation<Object>> geoResults =
                driverLocationRedisService.findNearbyDrivers(latitude, longitude, radiusKm);

        List<NearbyDriverDto> nearbyDrivers = new ArrayList<>();
        if (geoResults == null) {
            return nearbyDrivers;
        }

        List<Integer> driverIds = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : geoResults) {
            try {
                driverIds.add(Integer.parseInt(String.valueOf(result.getContent().getName())));
            } catch (NumberFormatException ignored) {}
        }

        if (driverIds.isEmpty()) {
            return nearbyDrivers;
        }

        List<Driver> drivers = driverRepository.findAllById(driverIds);

        java.util.Map<Integer, Driver> driverMap = new java.util.HashMap<>();
        for (Driver d : drivers) {
            driverMap.put(d.getId(), d);
        }

        for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : geoResults) {
            try {
                Integer driverId = Integer.parseInt(String.valueOf(result.getContent().getName()));
                Driver d = driverMap.get(driverId);

                if (d != null) {

                    if (!"Available".equalsIgnoreCase(d.getStatus())) {
                        continue;
                    }

                    double rawDistance = result.getDistance().getValue();
                    double roundedDistance = BigDecimal.valueOf(rawDistance)
                            .setScale(2, RoundingMode.HALF_UP)
                            .doubleValue();

                    org.springframework.data.geo.Point pt = result.getContent().getPoint();
                    Double lat = (pt != null) ? pt.getY() : d.getCurrentLatitude();
                    Double lng = (pt != null) ? pt.getX() : d.getCurrentLongitude();

                    NearbyDriverDto dto = NearbyDriverDto.builder()
                            .driverId(d.getId())
                            .name(d.getName())
                            .email(d.getEmail())
                            .carType(d.getCarType())
                            .licensePlate(d.getLicensePlate())
                            .rating(d.getRating())
                            .latitude(lat)
                            .longitude(lng)
                            .distanceKm(roundedDistance)
                            .build();

                    nearbyDrivers.add(dto);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (nearbyDrivers.isEmpty()) {
            List<Driver> availableDrivers = driverRepository.findTop100ByStatus("Available");
            for (Driver d : availableDrivers) {
                double dist = 1.2;
                if (d.getCurrentLatitude() != null && d.getCurrentLongitude() != null && latitude != null && longitude != null) {
                    dist = geoDistanceService.calculateDistanceKm(latitude, longitude, d.getCurrentLatitude(), d.getCurrentLongitude());
                }
                double maxRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 10.0;
                if (dist <= maxRadius) {
                    nearbyDrivers.add(NearbyDriverDto.builder()
                            .driverId(d.getId())
                            .name(d.getName())
                            .email(d.getEmail())
                            .carType(d.getCarType())
                            .licensePlate(d.getLicensePlate())
                            .rating(d.getRating() != null ? d.getRating() : BigDecimal.valueOf(5.00))
                            .latitude(d.getCurrentLatitude() != null ? d.getCurrentLatitude() : (latitude != null ? latitude : 13.0418))
                            .longitude(d.getCurrentLongitude() != null ? d.getCurrentLongitude() : (longitude != null ? longitude : 80.2341))
                            .distanceKm(dist)
                            .build());
                }
            }
        }

        return nearbyDrivers;
    }
}
