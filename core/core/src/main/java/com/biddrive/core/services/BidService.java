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
    private final AuctionEventPublisher auctionEventPublisher;

    private final DriverService driverService;

    public BidService(BidRepository bidRepository,
                      RideRepository rideRepository,
                      DriverRepository driverRepository,
                      AuctionRedisService auctionRedisService,
                      AuctionEventPublisher auctionEventPublisher,
                      DriverService driverService) {
        this.bidRepository = bidRepository;
        this.rideRepository = rideRepository;
        this.driverRepository = driverRepository;
        this.auctionRedisService = auctionRedisService;
        this.auctionEventPublisher = auctionEventPublisher;
        this.driverService = driverService;
    }

    public Bid createBid(Bid bid) {
        if (bid.getBidAmount() == null) {
            throw new RuntimeException("Bid amount is required.");
        }

        if (!driverService.isDriverAvailable(bid.getDriverId())) {
            throw new RuntimeException("Driver is not available to place bids.");
        }

        com.biddrive.core.models.Ride ride = rideRepository.findById(bid.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + bid.getRideId()));
        com.biddrive.core.models.Driver driver = driverRepository.findById(bid.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found with id: " + bid.getDriverId()));
        if (!com.biddrive.core.services.RideService.isVehicleCompatible(ride.getVehicleType(), driver.getCarType())) {
            throw new RuntimeException("Vehicle mismatch: Your vehicle (" + driver.getCarType()
                    + ") is not compatible with this ride's requested category (" + ride.getVehicleType() + ").");
        }

        auctionRedisService.submitLiveBid(bid.getRideId(), bid.getDriverId(), bid.getBidAmount());

        bid.setStatus("ACCEPTED_LOWEST");
        Bid savedBid = bidRepository.save(bid);

        auctionEventPublisher.publishNewBid(savedBid.getRideId(), savedBid.getId(), savedBid.getDriverId(), savedBid.getBidAmount());

        return savedBid;
    }

    @Transactional
    public Bid acceptBid(Integer bidId, Integer authenticatedPassengerId) {
        Bid acceptedBid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found with id: " + bidId));

        Ride ride = rideRepository.findById(acceptedBid.getRideId())
                .orElseThrow(() -> new RuntimeException("Ride not found"));

        if (!ride.getPassengerId().equals(authenticatedPassengerId)) {
            throw new RuntimeException("Access Denied: You do not own the ride associated with this bid.");
        }

        Driver driver = driverRepository.findById(acceptedBid.getDriverId())
                .orElseThrow(() -> new RuntimeException("Driver not found"));

        if (!"INITIATED".equalsIgnoreCase(ride.getStatus())) {
            throw new RuntimeException("Ride is no longer active for booking.");
        }

        acceptedBid.setStatus("ACCEPTED");
        bidRepository.save(acceptedBid);

        List<Bid> otherBids = bidRepository.findByRideId(ride.getId());
        for (Bid b : otherBids) {
            if (!b.getId().equals(acceptedBid.getId())) {
                b.setStatus("REJECTED");
                bidRepository.save(b);
            }
        }

        String otp = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
        ride.setStatus("MATCHED");
        ride.setDriverId(driver.getId());
        ride.setFinalFare(acceptedBid.getBidAmount());
        ride.setOtp(otp);
        rideRepository.save(ride);

        driver.setStatus("BUSY");
        driverRepository.save(driver);

        auctionRedisService.closeAuction(ride.getId());

        auctionEventPublisher.publishAuctionMatched(ride, driver.getId(), acceptedBid.getBidAmount());

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

    private boolean hasActiveMatchedTrip(Integer driverId) {
        return bidRepository.hasActiveMatchedTrip(driverId);
    }
}
