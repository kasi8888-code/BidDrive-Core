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

    @org.springframework.scheduling.annotation.Async
    public void publishRideCreated(Ride ride, java.util.List<com.biddrive.core.dtos.NearbyDriverDto> nearbyDrivers) {
        Long remainingSeconds = auctionRedisService.getRemainingSeconds(ride.getId());
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("RIDE_CREATED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .baseFare(ride.getBaseFare())
                .pickupLocation(ride.getPickupLocation())
                .destinationLocation(ride.getDestinationLocation())
                .pickupLatitude(ride.getPickupLatitude())
                .pickupLongitude(ride.getPickupLongitude())
                .destinationLatitude(ride.getDestinationLatitude())
                .destinationLongitude(ride.getDestinationLongitude())
                .estimatedDistanceKm(ride.getEstimatedDistanceKm())
                .status(ride.getStatus())
                .remainingSeconds(remainingSeconds > 0 ? remainingSeconds : 180L)
                .timestamp(LocalDateTime.now())
                .message("New ride #" + ride.getId() + " requested from "
                        + ride.getPickupLocation() + " to " + ride.getDestinationLocation()
                        + " with base fare ₹" + ride.getBaseFare())
                .build();

        messagingTemplate.convertAndSend("/topic/rides", event);

        if (nearbyDrivers != null && !nearbyDrivers.isEmpty()) {
            for (com.biddrive.core.dtos.NearbyDriverDto driver : nearbyDrivers) {
                if (driver.getEmail() != null) {
                    AuctionEventDto targetedEvent = AuctionEventDto.builder()
                            .eventType("RIDE_CREATED")
                            .rideId(ride.getId())
                            .passengerId(ride.getPassengerId())
                            .baseFare(ride.getBaseFare())
                            .pickupLocation(ride.getPickupLocation())
                            .destinationLocation(ride.getDestinationLocation())
                            .pickupLatitude(ride.getPickupLatitude())
                            .pickupLongitude(ride.getPickupLongitude())
                            .destinationLatitude(ride.getDestinationLatitude())
                            .destinationLongitude(ride.getDestinationLongitude())
                            .estimatedDistanceKm(ride.getEstimatedDistanceKm())
                            .driverDistanceToPickupKm(driver.getDistanceKm())
                            .status(ride.getStatus())
                            .remainingSeconds(remainingSeconds > 0 ? remainingSeconds : 180L)
                            .timestamp(LocalDateTime.now())
                            .message("🎯 Nearby Ride Alert! Ride #" + ride.getId() + " is "
                                    + driver.getDistanceKm() + " km away from your current location.")
                            .build();

                    messagingTemplate.convertAndSendToUser(driver.getEmail(), "/queue/rides", targetedEvent);

                }
            }
        }
    }

    public void publishRideCreated(Ride ride) {
        publishRideCreated(ride, null);
    }

    @org.springframework.scheduling.annotation.Async
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

    }

    @org.springframework.scheduling.annotation.Async
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

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);

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

        }

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            AuctionEventDto passengerNotification = AuctionEventDto.builder()
                    .eventType("DRIVER_CONFIRMED")
                    .rideId(ride.getId())
                    .driverId(winningDriverId)
                    .driverName(driverName)
                    .lowestBid(winningBidAmount)
                    .otp(ride.getOtp())
                    .status("MATCHED")
                    .timestamp(LocalDateTime.now())
                    .message("Your ride #" + ride.getId() + " has been booked with " + driverName + " for ₹" + winningBidAmount + ". Your trip OTP is " + ride.getOtp() + " (share this with driver on boarding).")
                    .build();

            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", passengerNotification);

        }
    }

    @org.springframework.scheduling.annotation.Async
    public void publishDriverArriving(Ride ride, Driver driver) {
        String driverName = driver != null && driver.getName() != null ? driver.getName() : "Driver #" + ride.getDriverId();
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("DRIVER_ARRIVING")
                .rideId(ride.getId())
                .driverId(ride.getDriverId())
                .driverName(driverName)
                .status("DRIVER_ARRIVING")
                .timestamp(LocalDateTime.now())
                .message(driverName + " is on the way to your pickup location.")
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }

    }

    @org.springframework.scheduling.annotation.Async
    public void publishDriverArrived(Ride ride, Driver driver) {
        String driverName = driver != null && driver.getName() != null ? driver.getName() : "Driver #" + ride.getDriverId();
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("DRIVER_ARRIVED")
                .rideId(ride.getId())
                .driverId(ride.getDriverId())
                .driverName(driverName)
                .status("ARRIVED")
                .timestamp(LocalDateTime.now())
                .message(driverName + " has ARRIVED at your pickup point! Please board and share your OTP (" + ride.getOtp() + ") to start the trip.")
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }

    }

    @org.springframework.scheduling.annotation.Async
    public void publishTripStarted(Ride ride) {
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("TRIP_STARTED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .driverId(ride.getDriverId())
                .status("IN_PROGRESS")
                .timestamp(LocalDateTime.now())
                .message("OTP verified successfully! Trip #" + ride.getId() + " is now IN_PROGRESS. Have a safe journey!")
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }

        if (ride.getDriverId() != null) {
            Driver driver = driverRepository.findById(ride.getDriverId()).orElse(null);
            if (driver != null && driver.getEmail() != null) {
                messagingTemplate.convertAndSendToUser(driver.getEmail(), "/queue/notifications", event);
            }
        }

    }

    @org.springframework.scheduling.annotation.Async
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

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }

    }

    @org.springframework.scheduling.annotation.Async
    public void publishRideCompleted(Ride ride) {
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("RIDE_COMPLETED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .driverId(ride.getDriverId())
                .status("COMPLETED")
                .timestamp(LocalDateTime.now())
                .message("Ride #" + ride.getId() + " has been COMPLETED. Driver is now Available.")
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);
        messagingTemplate.convertAndSend("/topic/rides", event);

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }

        if (ride.getDriverId() != null) {
            Driver driver = driverRepository.findById(ride.getDriverId()).orElse(null);
            if (driver != null && driver.getEmail() != null) {
                messagingTemplate.convertAndSendToUser(driver.getEmail(), "/queue/notifications", event);
            }
        }

    }

    @org.springframework.scheduling.annotation.Async
    public void publishTripCancelled(Ride ride, String reason, String cancelledBy) {
        AuctionEventDto event = AuctionEventDto.builder()
                .eventType("TRIP_CANCELLED")
                .rideId(ride.getId())
                .passengerId(ride.getPassengerId())
                .driverId(ride.getDriverId())
                .status("CANCELLED")
                .timestamp(LocalDateTime.now())
                .message("Ride #" + ride.getId() + " was CANCELLED by " + cancelledBy + (reason != null ? ": " + reason : "."))
                .build();

        messagingTemplate.convertAndSend("/topic/auction/" + ride.getId(), event);
        messagingTemplate.convertAndSend("/topic/rides", event);

        Passenger passenger = passengerRepository.findById(ride.getPassengerId()).orElse(null);
        if (passenger != null && passenger.getEmail() != null) {
            messagingTemplate.convertAndSendToUser(passenger.getEmail(), "/queue/notifications", event);
        }

        if (ride.getDriverId() != null) {
            Driver driver = driverRepository.findById(ride.getDriverId()).orElse(null);
            if (driver != null && driver.getEmail() != null) {
                messagingTemplate.convertAndSendToUser(driver.getEmail(), "/queue/notifications", event);
            }
        }

    }
}
