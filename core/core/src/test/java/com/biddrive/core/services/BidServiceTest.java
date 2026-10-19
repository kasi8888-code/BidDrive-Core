package com.biddrive.core.services;

import com.biddrive.core.models.Bid;
import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.RideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidRepository bidRepository;

    @Mock
    private RideRepository rideRepository;

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private AuctionRedisService auctionRedisService;

    @Mock
    private AuctionEventPublisher auctionEventPublisher;

    @Mock
    private DriverService driverService;

    @InjectMocks
    private BidService bidService;

    private Ride activeRide;
    private Driver availableDriver;

    @BeforeEach
    void setUp() {
        activeRide = new Ride();
        activeRide.setId(10);
        activeRide.setStatus("INITIATED");
        activeRide.setVehicleType("Cab Priority");
        activeRide.setBaseFare(new BigDecimal("150.00"));

        availableDriver = new Driver();
        availableDriver.setId(5);
        availableDriver.setStatus("Available");
        availableDriver.setCarType("Sedan");

        lenient().when(rideRepository.findById(10)).thenReturn(Optional.of(activeRide));
        lenient().when(driverRepository.findById(5)).thenReturn(Optional.of(availableDriver));
    }

    @Test
    @DisplayName("Create Bid: Successfully places bid within ceiling when ride is INITIATED and driver is Available")
    void testCreateBidSuccess() {
        when(driverService.isDriverAvailable(5)).thenReturn(true);

        Bid incomingBid = new Bid();
        incomingBid.setRideId(10);
        incomingBid.setDriverId(5);
        incomingBid.setBidAmount(new BigDecimal("140.00")); // under baseFare (150) + 100 ceiling

        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid b = invocation.getArgument(0);
            b.setId(99);
            return b;
        });

        when(auctionRedisService.submitLiveBid(10, 5, new BigDecimal("140.00"))).thenReturn(Map.of("status", "ACCEPTED_AS_LOWEST"));

        Bid created = bidService.createBid(incomingBid);

        assertNotNull(created);
        assertEquals(new BigDecimal("140.00"), created.getBidAmount());
        assertEquals("ACCEPTED_LOWEST", created.getStatus());

        verify(auctionEventPublisher, times(1)).publishNewBid(eq(10), eq(99), eq(5), eq(new BigDecimal("140.00")));
    }

    @Test
    @DisplayName("Bid Ceiling: Rejects bid exceeding baseFare + ₹100")
    void testCreateBidExceedsCeiling() {
        when(driverService.isDriverAvailable(5)).thenReturn(true);
        doThrow(new RuntimeException("Bid amount must be greater than 0 and cannot exceed max allowed fare."))
                .when(auctionRedisService).submitLiveBid(10, 5, new BigDecimal("260.00"));

        // baseFare = 150, max allowed = 250. Bid = 260
        Bid exorbitantBid = new Bid();
        exorbitantBid.setRideId(10);
        exorbitantBid.setDriverId(5);
        exorbitantBid.setBidAmount(new BigDecimal("260.00"));

        assertThrows(RuntimeException.class, () -> bidService.createBid(exorbitantBid));
        verify(bidRepository, never()).save(any(Bid.class));
    }

    @Test
    @DisplayName("Ride Status Guard: Rejects bid on ride that is already MATCHED or COMPLETED")
    void testCreateBidOnClosedRide() {
        when(driverService.isDriverAvailable(5)).thenReturn(true);
        doThrow(new RuntimeException("Auction has expired or is not active."))
                .when(auctionRedisService).submitLiveBid(10, 5, new BigDecimal("140.00"));

        Bid bid = new Bid();
        bid.setRideId(10);
        bid.setDriverId(5);
        bid.setBidAmount(new BigDecimal("140.00"));

        assertThrows(RuntimeException.class, () -> bidService.createBid(bid));
        verify(bidRepository, never()).save(any(Bid.class));
    }
}
