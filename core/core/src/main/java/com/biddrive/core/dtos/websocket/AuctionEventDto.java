package com.biddrive.core.dtos.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AuctionEventDto represents the real-time message payload sent over WebSockets (STOMP).
 *
 * Broadcast destinations:
 *   - /topic/rides : When a new ride is requested
 *   - /topic/auction/{rideId} : Live bid updates and auction resolution for a ride
 *   - /user/queue/notifications : Targeted direct notification for winner/passenger
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionEventDto {

    private String eventType; // RIDE_CREATED, NEW_BID, AUCTION_MATCHED, AUCTION_EXPIRED
    private Integer rideId;
    private Integer bidId;
    private Integer passengerId;
    private Integer driverId;
    private String driverName;
    private BigDecimal lowestBid;
    private BigDecimal baseFare;
    private String pickupLocation;
    private String destinationLocation;
    private String status;
    private Long remainingSeconds;
    private LocalDateTime timestamp;
    private String message;
}
