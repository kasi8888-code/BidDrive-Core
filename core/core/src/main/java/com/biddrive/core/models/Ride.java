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

    @Column(name = "pickup_location", nullable = false, length = 100)
    private String pickupLocation;

    @Column(name = "destination_location", nullable = false, length = 100)
    private String destinationLocation;

    @Column(name = "base_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFare;

    @Column(nullable = false)
    private String status = "INITIATED";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}


