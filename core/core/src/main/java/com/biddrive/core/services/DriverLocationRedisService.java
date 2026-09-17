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

/**
 * DriverLocationRedisService manages real-time driver GPS coordinates in Redis.
 *
 * Uses Redis native Geospatial Sorted Sets (GEOADD, GEORADIUS / GEOSEARCH)
 * to achieve sub-millisecond proximity queries without hitting relational databases.
 */
@Service
public class DriverLocationRedisService {

    public static final String DRIVERS_GEO_KEY = "drivers:locations";

    private final RedisTemplate<String, Object> redisTemplate;

    public DriverLocationRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Updates or inserts the driver's current position in Redis.
     * Note: Redis Geo commands take Longitude first, then Latitude (x, y).
     */
    public void updateDriverLocation(Integer driverId, Double latitude, Double longitude) {
        if (driverId == null || latitude == null || longitude == null) {
            return;
        }
        // x = longitude, y = latitude
        Point location = new Point(longitude, latitude);
        redisTemplate.opsForGeo().add(DRIVERS_GEO_KEY, location, String.valueOf(driverId));
    }

    /**
     * Searches for active drivers within a circular radius of the pickup location.
     * Results include coordinates and distance, sorted ascending (nearest drivers first).
     */
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
                .sortAscending();

        return redisTemplate.opsForGeo().radius(DRIVERS_GEO_KEY, searchArea, args);
    }

    /**
     * Fetches the current location of a driver if present in Redis.
     */
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

    /**
     * Removes a driver from the active geospatial index (e.g. when going offline).
     * Redis GEO structures are Sorted Sets (ZSET) internally.
     */
    public void removeDriverLocation(Integer driverId) {
        if (driverId != null) {
            redisTemplate.opsForZSet().remove(DRIVERS_GEO_KEY, String.valueOf(driverId));
        }
    }
}
