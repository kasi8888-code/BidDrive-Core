package com.biddrive.core.services;

import com.biddrive.core.models.Bid;
import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.RideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class BidService {

    private final BidRepository bidRepository;
    private final RideRepository rideRepository;
    private final DriverRepository driverRepository;
    private final AuctionRedisService auctionRedisService;

    public BidService(BidRepository bidRepository, 
                      RideRepository rideRepository, 
                      DriverRepository driverRepository,
                      AuctionRedisService auctionRedisService) {
        this.bidRepository = bidRepository;
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.auctionRedisService = auctionRedisService;
    }

    public Bid createBid(Bid bid) {
        // 1. Check if Ride exists
        Ride ride = rideRepository.findById(bid.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + bid.getRideId()));

        // 2. Check if Driver exists
        Driver driver = driverRepository.findById(bid.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + bid.getDriverId()));

        // 3. Rule: Driver must be Available
        if (!"Available".equalsIgnoreCase(driver.getStatus())) {
            throw new RuntimeException("Driver is not Available to place bids.");
        }

        // 4. Rule: Ride must be INITIATED (active auction)
        if (!"INITIATED".equalsIgnoreCase(ride.getStatus())) {
            throw new RuntimeException("Ride #" + ride.getId() + " is no longer open for bidding (Current status: " + ride.getStatus() + "). Please create a new ride or ensure you bid before the auction expires.");
        }

        // 5. Rule: Max allowed fare = baseFare + 100
        BigDecimal maxAllowedFare = ride.getBaseFare().add(BigDecimal.valueOf(100));

        if (bid.getBidAmount() == null 
                || bid.getBidAmount().compareTo(BigDecimal.ZERO) <= 0 
                || bid.getBidAmount().compareTo(maxAllowedFare) > 0) {
            throw new RuntimeException("Bid amount must be greater than 0 and cannot exceed max allowed fare of ₹" + maxAllowedFare + " (Base Fare + ₹100).");
        }

        // 6. Submit live bid to Redis In-Memory Engine (Atomically compares against lowest bid)
        auctionRedisService.submitLiveBid(bid.getRideId(), bid.getDriverId(), bid.getBidAmount());

        bid.setStatus("ACCEPTED_LOWEST");
        return bidRepository.save(bid);
    }


    /**
     * Passenger accepts a specific driver's bid.
     * Enforces ownership: only the passenger who created the ride can accept bids for it.
     */
    @Transactional
    public Bid acceptBid(Integer bidId, Integer authenticatedPassengerId) {
        Bid acceptedBid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found with id: " + bidId));

        Ride ride = rideRepository.findById(acceptedBid.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        // IDOR Protection: Verify authenticated passenger owns this ride
        if (!ride.getPassengerId().equals(authenticatedPassengerId)) {
            throw new RuntimeException("Access Denied: You do not own the ride associated with this bid.");
        }

        Driver driver = driverRepository.findById(acceptedBid.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        if (!"INITIATED".equalsIgnoreCase(ride.getStatus())) {
            throw new RuntimeException("Ride is no longer active for booking.");
        }

        // 1. Accept this bid
        acceptedBid.setStatus("ACCEPTED");
        bidRepository.save(acceptedBid);

        // 2. Reject all other bids for this ride
        List<Bid> otherBids = bidRepository.findByRideId(ride.getId());
        for (Bid b : otherBids) {
            if (!b.getId().equals(acceptedBid.getId())) {
                b.setStatus("REJECTED");
                bidRepository.save(b);
            }
        }

        // 3. Update Ride status to MATCHED
        ride.setStatus("MATCHED");
        rideRepository.save(ride);

        // 4. Update Driver status to BUSY
        driver.setStatus("BUSY");
        driverRepository.save(driver);

        return acceptedBid;
    }

    public List<Bid> getAllBids() {
        return bidRepository.findAll();
    }

    public Optional<Bid> getBidById(Integer id) {
        return bidRepository.findById(id);
    }

    public List<Bid> getBidsByRideId(Integer rideId) {
        return bidRepository.findByRideId(rideId);
    }
}

