package com.biddrive.core.services;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class GeoDistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private static final BigDecimal BASE_FLAG_FALL = BigDecimal.valueOf(50.00);
    private static final BigDecimal PER_KM_RATE = BigDecimal.valueOf(15.00);

    public double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double originLatRad = Math.toRadians(lat1);
        double destLatRad = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(originLatRad) * Math.cos(destLatRad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_KM * c;

        return BigDecimal.valueOf(distance)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public BigDecimal calculateSuggestedBaseFare(double distanceKm) {
        BigDecimal variableComponent = BigDecimal.valueOf(distanceKm)
                .multiply(PER_KM_RATE);

        return BASE_FLAG_FALL.add(variableComponent)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
