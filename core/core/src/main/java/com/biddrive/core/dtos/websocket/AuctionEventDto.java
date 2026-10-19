package com.biddrive.core.dtos.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEventDto {

    private String eventType;
    private Integer rideId;
    private Integer bidId;
    private Integer passengerId;
    private Integer driverId;
    private String driverName;
    private BigDecimal lowestBid;
    private BigDecimal baseFare;
    private String pickupLocation;
    private String destinationLocation;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Double estimatedDistanceKm;
    private Double driverDistanceToPickupKm;
    private String status;
    private Long remainingSeconds;
    private String otp;
    private LocalDateTime timestamp;
    private String message;
}
