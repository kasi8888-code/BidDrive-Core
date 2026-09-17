package com.biddrive.core.services;

import com.biddrive.core.dtos.NearbyDriverDto;
import com.biddrive.core.models.Bid;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.repositories.RideRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final PassengerRepository passengerRepository;
    private final BidRepository bidRepository;
    private final AuctionRedisService auctionRedisService;
    private final AuctionEventPublisher auctionEventPublisher;
    private final GeoDistanceService geoDistanceService;
    private final DriverService driverService;

    public RideService(RideRepository rideRepository, 
                       PassengerRepository passengerRepository,
                       BidRepository bidRepository,
                       AuctionRedisService auctionRedisService,
                       AuctionEventPublisher auctionEventPublisher,
                       GeoDistanceService geoDistanceService,
                       DriverService driverService) {
        this.rideRepository = rideRepository;
        this.passengerRepository = passengerRepository;
        this.bidRepository = bidRepository;
        this.auctionRedisService = auctionRedisService;
        this.auctionEventPublisher = auctionEventPublisher;
        this.geoDistanceService = geoDistanceService;
        this.driverService = driverService;
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Optional<Ride> getRideById(Integer id) {
        return rideRepository.findById(id);
    }

    public java.util.Map<Object, Object> getLiveAuction(Integer rideId) {
        return auctionRedisService.getAuction(rideId);
    }

    public Ride createRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }

        // 1. Calculate Great-Circle distance and suggested fare if coordinates are provided
        if (ride.getPickupLatitude() != null && ride.getPickupLongitude() != null
                && ride.getDestinationLatitude() != null && ride.getDestinationLongitude() != null) {
            double distanceKm = geoDistanceService.calculateDistanceKm(
                    ride.getPickupLatitude(), ride.getPickupLongitude(),
                    ride.getDestinationLatitude(), ride.getDestinationLongitude());
            ride.setEstimatedDistanceKm(distanceKm);

            // Auto-populate realistic base fare if not explicitly set
            if (ride.getBaseFare() == null || ride.getBaseFare().compareTo(BigDecimal.ZERO) <= 0) {
                ride.setBaseFare(geoDistanceService.calculateSuggestedBaseFare(distanceKm));
            }
        }

        if (ride.getBaseFare() == null) {
            ride.setBaseFare(BigDecimal.valueOf(50.00));
        }

        // 2. Save to SQL
        Ride savedRide = rideRepository.save(ride);

        // 3. Instantiate Ephemeral Auction in Redis
        auctionRedisService.initAuction(savedRide.getId(), savedRide.getBaseFare());

        // 4. Discover nearby drivers (within 5 km radius) from Redis Geospatial cache
        List<NearbyDriverDto> nearbyDrivers = null;
        if (savedRide.getPickupLatitude() != null && savedRide.getPickupLongitude() != null) {
            nearbyDrivers = driverService.findNearbyDrivers(
                    savedRide.getPickupLatitude(), savedRide.getPickupLongitude(), 5.0);
        }

        // 5. Broadcast real-time event to /topic/rides and dispatch targeted alerts to nearby drivers
        auctionEventPublisher.publishRideCreated(savedRide, nearbyDrivers);

        return savedRide;
    }


    public Ride updateRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }
        return rideRepository.save(ride);
    }

    public void deleteRide(Integer id, Integer authenticatedPassengerId) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + id));

        if (!ride.getPassengerId().equals(authenticatedPassengerId)) {
            throw new RuntimeException("Access Denied: You do not own this ride.");
        }

        rideRepository.deleteById(id);
    }

    /**
     * Completes an active ride.
     * Updates ride status to COMPLETED and automatically reverts the assigned driver's status to Available.
     */
    public Ride completeRide(Integer rideId) {
        Ride ride = rideRepository.findById(rideId)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + rideId));

        if (!"MATCHED".equalsIgnoreCase(ride.getStatus())) {
            throw new RuntimeException("Cannot complete ride #" + rideId + ". Status must be MATCHED (current: " + ride.getStatus() + ").");
        }

        // 1. Update Ride status to COMPLETED
        ride.setStatus("COMPLETED");
        Ride savedRide = rideRepository.save(ride);

        // 2. Identify the assigned driver from the accepted bid and mark them Available
        List<Bid> bids = bidRepository.findByRideId(rideId);
        for (Bid b : bids) {
            if (b.getStatus() != null && b.getStatus().startsWith("ACCEPTED")) {
                driverService.updateDriverStatus(b.getDriverId(), "Available");
                System.out.println("✅ [Ride Completed] Driver #" + b.getDriverId() + " marked as Available again.");
                break;
            }
        }

        // 3. Broadcast ride completion event
        auctionEventPublisher.publishRideCompleted(savedRide);

        return savedRide;
    }
}
