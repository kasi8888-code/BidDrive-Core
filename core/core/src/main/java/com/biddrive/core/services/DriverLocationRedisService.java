package com.biddrive.core.services;

import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DriverLocationRedisService {

    public static final String DRIVERS_GEO_KEY = "drivers:locations";

    private final RedisTemplate<String, Object> redisTemplate;

    public DriverLocationRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void updateDriverLocation(Integer driverId, Double latitude, Double longitude) {
        if (driverId == null || latitude == null || longitude == null) {
            return;
        }

        Point location = new Point(longitude, latitude);
        redisTemplate.opsForGeo().add(DRIVERS_GEO_KEY, location, String.valueOf(driverId));
    }

    public GeoResults<RedisGeoCommands.GeoLocation<Object>> findNearbyDrivers(Double pickupLat, Double pickupLng, Double radiusKm) {
        if (pickupLat == null || pickupLng == null) {
            return null;
        }

        double searchRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : 5.0;

        Point pickupPoint = new Point(pickupLng, pickupLat);
        Distance distance = new Distance(searchRadius, Metrics.KILOMETERS);
        Circle searchArea = new Circle(pickupPoint, distance);

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending()
                .limit(100);

        return redisTemplate.opsForGeo().radius(DRIVERS_GEO_KEY, searchArea, args);
    }

    public Point getDriverLocation(Integer driverId) {
        if (driverId == null) {
            return null;
        }
        List<Point> points = redisTemplate.opsForGeo().position(DRIVERS_GEO_KEY, String.valueOf(driverId));
        if (points != null && !points.isEmpty()) {
            return points.get(0);
        }
        return null;
    }

    public void removeDriverLocation(Integer driverId) {
        if (driverId != null) {
            redisTemplate.opsForZSet().remove(DRIVERS_GEO_KEY, String.valueOf(driverId));
        }
    }
}
