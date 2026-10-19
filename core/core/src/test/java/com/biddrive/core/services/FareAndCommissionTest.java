package com.biddrive.core.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FareAndCommissionTest {

    private FareCalculationService fareService;

    @BeforeEach
    void setUp() {
        fareService = new FareCalculationService();
        // Inject configurable rates
        ReflectionTestUtils.setField(fareService, "perKmRate", 12.0);
        ReflectionTestUtils.setField(fareService, "perMinuteRate", 1.5);
        ReflectionTestUtils.setField(fareService, "minimumFare", 50.0);
        ReflectionTestUtils.setField(fareService, "platformCommissionRate", 0.20); // 20% instant
        ReflectionTestUtils.setField(fareService, "auctionCommissionRate", 0.15);  // 15% auction
    }

    @Test
    @DisplayName("Fare Calculation: (10km * 12) + (20min * 1.5) = ₹150.00")
    void testCalculateFareStandardTrip() {
        BigDecimal fare = fareService.calculateFare(10.0, 20.0);
        assertEquals(new BigDecimal("150.00"), fare);
    }

    @Test
    @DisplayName("Minimum Fare Enforcement: Short trip under ₹50 floors at ₹50.00")
    void testMinimumFareFloor() {
        // (1km * 12) + (2min * 1.5) = ₹15.00 -> should floor at ₹50.00
        BigDecimal fare = fareService.calculateFare(1.0, 2.0);
        assertEquals(new BigDecimal("50.00"), fare);
    }

    @Test
    @DisplayName("Commission: Instant ride takes 20% platform commission, driver keeps 80%")
    void testInstantRideCommission() {
        BigDecimal grossFare = new BigDecimal("200.00");
        BigDecimal driverEarning = fareService.calculateDriverEarning(grossFare, "INSTANT");
        BigDecimal commission = fareService.calculateCommission(grossFare, "INSTANT");

        assertEquals(new BigDecimal("160.00"), driverEarning, "Driver should receive 80%");
        assertEquals(new BigDecimal("40.00"), commission, "Platform should receive 20%");
    }

    @Test
    @DisplayName("Commission: Auction ride takes 15% platform commission, driver keeps 85%")
    void testAuctionRideCommission() {
        BigDecimal grossFare = new BigDecimal("200.00");
        BigDecimal driverEarning = fareService.calculateDriverEarning(grossFare, "AUCTION");
        BigDecimal commission = fareService.calculateCommission(grossFare, "AUCTION");

        assertEquals(new BigDecimal("170.00"), driverEarning, "Driver should receive 85% in auction");
        assertEquals(new BigDecimal("30.00"), commission, "Platform should receive 15% in auction");
    }

    @Test
    @DisplayName("Default ride type should fallback to INSTANT 20% commission")
    void testDefaultRideTypeFallsBackToInstant() {
        BigDecimal grossFare = new BigDecimal("100.00");
        BigDecimal driverEarning = fareService.calculateDriverEarning(grossFare);
        assertEquals(new BigDecimal("80.00"), driverEarning);
    }
}
