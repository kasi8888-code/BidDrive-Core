package com.biddrive.core.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "ratings")
@Data
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "ride_id", nullable = false)
    private Integer rideId;

    @Column(name = "rater_id", nullable = false)
    private Integer raterId;

    @Column(name = "ratee_id", nullable = false)
    private Integer rateeId;

    @Column(nullable = false)
    private Integer score;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
