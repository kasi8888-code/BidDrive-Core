package com.biddrive.core.services;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.repositories.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RideLifecycleServiceTest {

    @Mock
    private RideRepository rideRepository;
    @Mock
    private PassengerRepository passengerRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private AuctionRedisService auctionRedisService;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
    @Mock
    private GeoDistanceService geoDistanceService;
    @Mock
    private DriverService driverService;
    @Mock
    private WalletService walletService;
    @Mock
    private FareCalculationService fareCalculationService;
    @Mock
    private com.biddrive.core.repositories.DriverRepository driverRepository;

    @InjectMocks
    private RideService rideService;

    private Ride matchedRide;
    private Driver driver;

    @BeforeEach
    void setUp() {
        matchedRide = new Ride();
        matchedRide.setId(101);
        matchedRide.setPassengerId(1);
        matchedRide.setDriverId(2);
        matchedRide.setBaseFare(BigDecimal.valueOf(150.00));
        matchedRide.setFinalFare(BigDecimal.valueOf(130.00));
        matchedRide.setOtp("4829");
        matchedRide.setStatus("MATCHED");
        matchedRide.setPickupLocation("Koramangala");
        matchedRide.setDestinationLocation("Indiranagar");

        driver = new Driver();
        driver.setId(2);
        driver.setName("Ramesh");
        driver.setEmail("driver@example.com");
        driver.setStatus("BUSY");
    }

    @Test
    @DisplayName("Driver can mark arriving when ride is MATCHED")
    void testMarkDriverArrivingSuccess() {
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));
        when(driverService.getDriverById(2)).thenReturn(Optional.of(driver));

        Ride updated = rideService.markDriverArriving(101, 2);

        assertEquals("DRIVER_ARRIVING", updated.getStatus());
        verify(auctionEventPublisher).publishDriverArriving(eq(updated), eq(driver));
    }

    @Test
    @DisplayName("Unassigned driver cannot mark arriving (AccessDeniedException)")
    void testMarkDriverArrivingUnauthorized() {
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));

        assertThrows(AccessDeniedException.class, () -> {
            rideService.markDriverArriving(101, 999); // Wrong driver ID
        });
    }

    @Test
    @DisplayName("Driver can mark arrived at pickup location")
    void testMarkDriverArrivedSuccess() {
        matchedRide.setStatus("DRIVER_ARRIVING");
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));
        when(driverService.getDriverById(2)).thenReturn(Optional.of(driver));

        Ride updated = rideService.markDriverArrived(101, 2);

        assertEquals("ARRIVED", updated.getStatus());
        verify(auctionEventPublisher).publishDriverArrived(eq(updated), eq(driver));
    }

    @Test
    @DisplayName("Invalid OTP is rejected when starting trip")
    void testStartTripInvalidOtp() {
        matchedRide.setStatus("ARRIVED");
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            rideService.startTrip(101, 2, "0000"); // Wrong OTP
        });

        assertTrue(ex.getMessage().contains("Invalid OTP"));
        assertEquals("ARRIVED", matchedRide.getStatus()); // Status must not change
    }

    @Test
    @DisplayName("Valid OTP verifies boarding and transitions to IN_PROGRESS")
    void testStartTripValidOtpSuccess() {
        matchedRide.setStatus("ARRIVED");
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));

        Ride updated = rideService.startTrip(101, 2, "4829");

        assertEquals("IN_PROGRESS", updated.getStatus());
        assertNotNull(updated.getStartedAt());
        verify(auctionEventPublisher).publishTripStarted(eq(updated));
    }

    @Test
    @DisplayName("Complete ride sets status to COMPLETED and reverts driver to Available")
    void testCompleteRideSuccess() {
        matchedRide.setStatus("IN_PROGRESS");
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));

        Ride updated = rideService.completeRide(101, 2);

        assertEquals("COMPLETED", updated.getStatus());
        assertNotNull(updated.getCompletedAt());
        verify(driverService).updateDriverStatus(2, "Available");
        verify(auctionEventPublisher).publishRideCompleted(eq(updated));
    }

    @Test
    @DisplayName("Cannot complete ride if status is not IN_PROGRESS")
    void testCompleteRideWrongState() {
        matchedRide.setStatus("MATCHED"); // Not IN_PROGRESS yet
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));

        assertThrows(IllegalStateException.class, () -> {
            rideService.completeRide(101, 2);
        });
    }

    @Test
    @DisplayName("Passenger can cancel ride before pickup and driver is released")
    void testCancelRidePassengerSuccess() {
        matchedRide.setStatus("DRIVER_ARRIVING");
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));
        when(rideRepository.save(any(Ride.class))).thenAnswer(i -> i.getArgument(0));

        Ride cancelled = rideService.cancelRide(101, 1, "ROLE_PASSENGER", "Driver took too long");

        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("PASSENGER", cancelled.getCancelledBy());
        assertEquals("Driver took too long", cancelled.getCancellationReason());
        assertNotNull(cancelled.getCancelledAt());
        verify(driverService).updateDriverStatus(2, "Available");
        verify(auctionEventPublisher).publishTripCancelled(eq(cancelled), eq("Driver took too long"), eq("PASSENGER"));
    }

    @Test
    @DisplayName("Cannot cancel ride once IN_PROGRESS")
    void testCancelRideInProgressFails() {
        matchedRide.setStatus("IN_PROGRESS");
        when(rideRepository.findById(101)).thenReturn(Optional.of(matchedRide));

        assertThrows(IllegalStateException.class, () -> {
            rideService.cancelRide(101, 1, "ROLE_PASSENGER", "Want to cancel mid-ride");
        });
    }
}
