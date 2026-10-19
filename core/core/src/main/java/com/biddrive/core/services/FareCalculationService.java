package com.biddrive.core.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FareCalculationService {

    @Value("${fare.per-km-rate:12.0}")
    private double perKmRate;

    @Value("${fare.per-minute-rate:1.5}")
    private double perMinuteRate;

    @Value("${fare.minimum-fare:50.0}")
    private double minimumFare;

    @Value("${fare.platform-commission-rate:0.20}")
    private double platformCommissionRate;

    @Value("${fare.auction-commission-rate:0.0}")
    private double auctionCommissionRate;

    public BigDecimal calculateFare(double distanceKm, double durationMinutes) {
        double calculated = (distanceKm * perKmRate) + (durationMinutes * perMinuteRate);
        double finalFare = Math.max(minimumFare, calculated);
        return BigDecimal.valueOf(finalFare).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateDriverEarning(BigDecimal grossFare) {
        return calculateDriverEarning(grossFare, "INSTANT");
    }

    public BigDecimal calculateDriverEarning(BigDecimal grossFare, String rideType) {
        double rate = "AUCTION".equalsIgnoreCase(rideType) ? auctionCommissionRate : platformCommissionRate;
        BigDecimal keepRate = BigDecimal.ONE.subtract(BigDecimal.valueOf(rate));
        return grossFare.multiply(keepRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateCommission(BigDecimal grossFare) {
        return calculateCommission(grossFare, "INSTANT");
    }

    public BigDecimal calculateCommission(BigDecimal grossFare, String rideType) {
        double rate = "AUCTION".equalsIgnoreCase(rideType) ? auctionCommissionRate : platformCommissionRate;
        return grossFare.multiply(BigDecimal.valueOf(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    public double getPlatformCommissionRate() {
        return platformCommissionRate;
    }

    public double getAuctionCommissionRate() {
        return auctionCommissionRate;
    }
}
