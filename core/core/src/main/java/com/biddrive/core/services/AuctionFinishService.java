package com.biddrive.core.services;

import com.biddrive.core.models.Bid;
import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.BidRepository;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.RideRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class AuctionFinishService {

    private static final Logger log = LoggerFactory.getLogger(AuctionFinishService.class);

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
            log.info("⏰ [Auction Expired] Ride #{} expired with NO bids.", rideId);

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

            log.info("🏆 [Auction Matched] Ride #{} matched with Driver #{} at lowest bid \u20b9{}",
                    rideId, winningBid.getDriverId(), winningBid.getBidAmount());

            auctionEventPublisher.publishAuctionMatched(savedRide, winningBid.getDriverId(), winningBid.getBidAmount());
        }
    }
}
