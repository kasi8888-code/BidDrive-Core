package com.biddrive.core.models;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rides")
@Data
public class Ride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "passenger_id", nullable = false)
    private Integer passengerId;

    @Column(name = "ride_type", length = 20)
    private String rideType = "AUCTION";

    @Column(name = "vehicle_type", length = 20)
    private String vehicleType = "Cab Economy";

    @Column(name = "pickup_location", nullable = false, length = 100)
    private String pickupLocation;

    @Column(name = "destination_location", nullable = false, length = 100)
    private String destinationLocation;

    @Column(name = "pickup_latitude")
    private Double pickupLatitude;

    @Column(name = "pickup_longitude")
    private Double pickupLongitude;

    @Column(name = "destination_latitude")
    private Double destinationLatitude;

    @Column(name = "destination_longitude")
    private Double destinationLongitude;

    @Column(name = "estimated_distance_km")
    private Double estimatedDistanceKm;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(nullable = false)
    private String status = "INITIATED";

    @Column(name = "driver_id")
    private Integer driverId;

    @Column(name = "final_fare", precision = 10, scale = 2)
    private BigDecimal finalFare;

    @Column(name = "otp", length = 10)
    private String otp;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "cancelled_by", length = 50)
    private String cancelledBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
