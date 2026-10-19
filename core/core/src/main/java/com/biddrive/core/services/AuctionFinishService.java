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

    @Transactional
    public void finalizeAuction(Integer rideId) {
        Optional<Ride> rideOpt = rideRepository.findById(rideId);
        if (rideOpt.isEmpty()) {
            return;
        }

        Ride ride = rideOpt.get();

        if (!"INITIATED".equalsIgnoreCase(ride.getStatus())) {
            return;
        }

        List<Bid> bids = bidRepository.findByRideId(rideId);

        if (bids.isEmpty()) {

            ride.setStatus("EXPIRED");
            rideRepository.save(ride);
            System.out.println("⏰ [Redis Auction Expired] Ride #" + rideId + " expired with NO bids.");

            auctionEventPublisher.publishAuctionExpired(ride);
        } else {

            Bid winningBid = bids.stream()
                    .min(Comparator.comparing(Bid::getBidAmount))
                    .orElse(bids.get(0));

            winningBid.setStatus("ACCEPTED_LOWEST");
            bidRepository.save(winningBid);

            for (Bid b : bids) {
                if (!b.getId().equals(winningBid.getId())) {
                    b.setStatus("REJECTED");
                    bidRepository.save(b);
                }
            }

            String otp = String.format("%04d", new java.security.SecureRandom().nextInt(10000));
            ride.setStatus("MATCHED");
            ride.setDriverId(winningBid.getDriverId());
            ride.setFinalFare(winningBid.getBidAmount());
            ride.setOtp(otp);
            Ride savedRide = rideRepository.save(ride);

            Optional<Driver> driverOpt = driverRepository.findById(winningBid.getDriverId());
            if (driverOpt.isPresent()) {
                Driver driver = driverOpt.get();
                driver.setStatus("BUSY");
                driverRepository.save(driver);
            }

            System.out.println("🏆 [Redis Auction Completed] Ride #" + rideId
                    + " MATCHED with Driver #" + winningBid.getDriverId()
                    + " at lowest bid ₹" + winningBid.getBidAmount());

            auctionEventPublisher.publishAuctionMatched(savedRide, winningBid.getDriverId(), winningBid.getBidAmount());
        }
    }
}
