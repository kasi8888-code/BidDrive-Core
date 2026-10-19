package com.biddrive.core.models;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bids")
@Data
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ride_id", nullable = false)
    private Integer rideId;

    @Column(name = "driver_id", nullable = false)
    private Integer driverId;

    @Column(name = "bid_amount", nullable = false, precision = 10, scale = 2)
    @JsonAlias({"amount", "bidAmount"})
    private BigDecimal bidAmount;

    @JsonProperty("amount")
    public void setAmount(BigDecimal amount) {
        if (this.bidAmount == null) {
            this.bidAmount = amount;
        }
    }

    public BigDecimal getAmount() {
        return this.bidAmount;
    }

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
