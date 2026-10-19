package com.biddrive.core.models;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "driver_wallets")
@Data
public class DriverWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "driver_id", nullable = false, unique = true)
    private Integer driverId;

    @Column(name = "available_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "total_gross_earnings", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalGrossEarnings = BigDecimal.ZERO;

    @Column(name = "total_commission_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalCommissionPaid = BigDecimal.ZERO;

    @Column(name = "total_trips_completed", nullable = false)
    private Integer totalTripsCompleted = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
