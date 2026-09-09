package com.biddrive.core.services;

import com.biddrive.core.dtos.websocket.AuctionEventDto;
import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Passenger;
import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.PassengerRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AuctionEventPublisher centralizes real-time messaging across WebSocket topics and user queues.
 */
@Service
public class AuctionEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;
    private final AuctionRedisService auctionRedisService;

    public AuctionEventPublisher(SimpMessagingTemplate messagingTemplate,
                                 DriverRepository driverRepository,
                                 PassengerRepository passengerRepository,
                                 AuctionRedisService auctionRedisService) {
        this.messagingTemplate = messagingTemplate;
        this.driverRepository = driverRepository;
        this.passengerRepository = passengerRepository;
        this.auctionRedisService = auctionRedisService;
    }

    /**
     * Broadcasts newly created ride to all available drivers listening on /topic/rides.
     */
    public void publishRideCreated(Ride ride) {
        Long remainingSeconds = auctionRedisService.getRemainingSeconds(ride.getId());
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("RIDE_CREATED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .baseFare(ride.getBaseFare())
                .pickupLocation(ride.getPickupLocation())
                .destinationLocation(ride.getDestinationLocation())
                .status(ride.getStatus())
                .remainingSeconds(remainingSeconds > 0 ? remainingSeconds : 180L)
                .timestamp(LocalDateTime.now())
                .message("New ride #" + ride.getId() + " requested from " 
                        + ride.getPickupLocation() + " to " + ride.getDestinationLocation() 
                        + " with base fare ₹" + ride.getBaseFare())
                .build();

        messagingTemplate.convertAndSend("/topic/rides", event);
        System.out.println("📢 [WebSocket Broadcast] New Ride posted to /topic/rides: Ride #" + ride.getId());
    }

    /**
     * Broadcasts new lowest bid placed by a driver to all listeners on /topic/auction/{rideId}.
     */
    public void publishNewBid(Integer rideId, Integer bidId, Integer driverId, BigDecimal bidAmount) {
        String driverName = "Driver #" + driverId;
        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver != null && driver.getName() != null) {
            driverName = driver.getName();
        }

        Long remainingSeconds = auctionRedisService.getRemainingSeconds(rideId);
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("NEW_BID")
                .rideId(rideId)
                .bidId(bidId)
                .driverId(driverId)
                .driverName(driverName)
                .lowestBid(bidAmount)
                .remainingSeconds(remainingSeconds)
                .timestamp(LocalDateTime.now())
                .message("New lowest bid placed by " + driverName + ": ₹" + bidAmount)
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + rideId, event);
        System.out.println("📢 [WebSocket Broadcast] New Lowest Bid on /topic/auction/" + rideId 
                + ": ₹" + bidAmount + " by " + driverName + " (bidId=" + bidId + ", TTL=" + remainingSeconds + "s)");
    }

    /**
     * Broadcasts auction match resolution (manual acceptance or timer expiry match).
     * Also pushes direct targeted notifications to winning driver and passenger.
     */
    public void publishAuctionMatched(Ride ride, Integer winningDriverId, BigDecimal winningBidAmount) {
        String driverName = "Driver #" + winningDriverId;
        Driver driver = driverRepository.findById(winningDriverId).orElse(null);
        if (driver != null && driver.getName() != null) {
            driverName = driver.getName();
        }

        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("AUCTION_MATCHED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .driverId(winningDriverId)
                .driverName(driverName)
                .lowestBid(winningBidAmount)
                .pickupLocation(ride.getPickupLocation())
                .destinationLocation(ride.getDestinationLocation())
                .status("MATCHED")
                .timestamp(LocalDateTime.now())
                .message("Ride #" + ride.getId() + " matched with " + driverName + " for ₹" + winningBidAmount)
                .build();

        // 1. Broadcast to everyone in the ride's auction room
        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);

        // 2. Private push notification to winning driver
        if (driver != null && driver.getEmail() != null) {
            AuctionEventDto driverNotification = AuctionEventDto.builder()
                    .eventType("RIDE_AWARDED")
                    .rideId(ride.getId())
                    .lowestBid(winningBidAmount)
                    .pickupLocation(ride.getPickupLocation())
                    .destinationLocation(ride.getDestinationLocation())
                    .status("MATCHED")
                    .timestamp(LocalDateTime.now())
                    .message("Congratulations! You won Ride #" + ride.getId() + " for ₹" + winningBidAmount + ". Please proceed to pickup.")
                    .build();

            messagingTemplate.convertAndSendToUser(driver.getEmail(), "/queue/notifications", driverNotification);
            System.out.println("🔔 [WebSocket Direct] Sent WIN notification to driver " + driver.getEmail());
        }

        // 3. Private push notification to passenger
        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            AuctionEventDto passengerNotification = AuctionEventDto.builder()
                    .eventType("DRIVER_CONFIRMED")
                    .rideId(ride.getId())
                    .driverId(winningDriverId)
                    .driverName(driverName)
                    .lowestBid(winningBidAmount)
                    .status("MATCHED")
                    .timestamp(LocalDateTime.now())
                    .message("Your ride #" + ride.getId() + " has been booked with " + driverName + " for ₹" + winningBidAmount)
                    .build();

            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", passengerNotification);
            System.out.println("🔔 [WebSocket Direct] Sent MATCH notification to passenger " + passenger.getEmail());
        }
    }

    /**
     * Broadcasts auction expiration when no bids were submitted during the window.
     */
    public void publishAuctionExpired(Ride ride) {
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("AUCTION_EXPIRED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .status("EXPIRED")
                .timestamp(LocalDateTime.now())
                .message("Auction for Ride #" + ride.getId() + " has expired with no bids.")
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);

        // Private push notification to passenger
        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }
        System.out.println("📢 [WebSocket Broadcast] Ride #" + ride.getId() + " expired.");
    }
}
