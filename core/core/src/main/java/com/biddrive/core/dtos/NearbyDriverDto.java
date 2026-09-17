package com.biddrive.core.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyDriverDto {
    private Integer driverId;
    private String name;
    private String email;
    private String carType;
    private String licensePlate;
    private BigDecimal rating;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
}
