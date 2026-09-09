package com.biddrive.core.services;

import com.biddrive.core.models.Bid;
import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.RideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AuctionFinishService {

    private final RideRepository rideRepository;
    private final BidRepository bidRepository;
    private final DriverRepository driverRepository;
    private final AuctionEventPublisher auctionEventPublisher;

    public AuctionFinishService(RideRepository rideRepository,
                                BidRepository bidRepository,
                                DriverRepository driverRepository,
                                AuctionEventPublisher auctionEventPublisher) {
        this.rideRepository = rideRepository;
        this.bidRepository = bidRepository;
        this.driverRepository = driverRepository;
        this.auctionEventPublisher = auctionEventPublisher;
    }

    /**
     * Finalizes the auction when the 60-second Redis timer expires.
     */
    @Transactional
    public void finalizeAuction(Integer rideId) {
        Optional<Ride> rideOpt = rideRepository.findById(rideId);
        if (rideOpt.isEmpty()) {
            return;
        }

        Ride ride = rideOpt.get();

        // Only process if the ride is still in INITIATED state
        if (!"INITIATED".equalsIgnoreCase(ride.getStatus())) {
            return;
        }

        List<Bid> bids = bidRepository.findByRideId(rideId);

        if (bids.isEmpty()) {
            // No drivers submitted bids during the 60s window
            ride.setStatus("EXPIRED");
            rideRepository.save(ride);
            System.out.println("⏰ [Redis Auction Expired] Ride #" + rideId + " expired with NO bids.");

            // Broadcast expiration over WebSockets
            auctionEventPublisher.publishAuctionExpired(ride);
        } else {
            // Find the lowest bid
            Bid winningBid = bids.stream()
                    .min(Comparator.comparing(Bid::getBidAmount))
                    .orElse(bids.get(0));

            winningBid.setStatus("ACCEPTED_LOWEST");
            bidRepository.save(winningBid);

            // Update Ride status to MATCHED
            ride.setStatus("MATCHED");
            rideRepository.save(ride);

            // Update winning Driver status to BUSY
            Optional<Driver> driverOpt = driverRepository.findById(winningBid.getDriverId());
            if (driverOpt.isPresent()) {
                Driver driver = driverOpt.get();
                driver.setStatus("BUSY");
                driverRepository.save(driver);
            }

            System.out.println("🏆 [Redis Auction Completed] Ride #" + rideId 
                    + " MATCHED with Driver #" + winningBid.getDriverId() 
                    + " at lowest bid ₹" + winningBid.getBidAmount());

            // Broadcast match over WebSockets and notify winning driver directly
            auctionEventPublisher.publishAuctionMatched(ride, winningBid.getDriverId(), winningBid.getBidAmount());
        }
    }
}
