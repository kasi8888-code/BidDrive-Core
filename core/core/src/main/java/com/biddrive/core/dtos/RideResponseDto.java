package com.biddrive.core.dtos;

import com.biddrive.core.models.Ride;
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
public class RideResponseDto {

    private Integer id;
    private Integer passengerId;
    private Integer driverId;
    private String pickupLocation;
    private String destinationLocation;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private Double estimatedDistanceKm;
    private BigDecimal baseFare;
    private BigDecimal finalFare;
    private String status;
    private String otp;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private String cancelledBy;
    private String rideType;
    private String vehicleType;
    private LocalDateTime createdAt;
    private Long remainingSeconds;

    private String driverName;
    private String driverPhone;
    private String driverCarType;
    private String driverLicensePlate;
    private BigDecimal driverRating;
    private Double driverLatitude;
    private Double driverLongitude;
    private String driverEta;

    public static RideResponseDto fromEntity(Ride ride, Integer requesterUserId, String requesterRole) {
        return fromEntity(ride, null, requesterUserId, requesterRole);
    }

    public static RideResponseDto fromEntity(Ride ride, com.biddrive.core.models.Driver driver, Integer requesterUserId, String requesterRole) {
        if (ride == null) return null;

        boolean isPassengerOwner = requesterUserId != null
                && (requesterRole != null && requesterRole.contains("PASSENGER"))
                && requesterUserId.equals(ride.getPassengerId());

        RideResponseDtoBuilder builder = RideResponseDto.builder()
                .id(ride.getId())
                .passengerId(ride.getPassengerId())
                .driverId(ride.getDriverId())
                .pickupLocation(ride.getPickupLocation())
                .destinationLocation(ride.getDestinationLocation())
                .pickupLatitude(ride.getPickupLatitude())
                .pickupLongitude(ride.getPickupLongitude())
                .destinationLatitude(ride.getDestinationLatitude())
                .destinationLongitude(ride.getDestinationLongitude())
                .estimatedDistanceKm(ride.getEstimatedDistanceKm())
                .baseFare(ride.getBaseFare())
                .finalFare(ride.getFinalFare())
                .status(ride.getStatus())
                .otp(isPassengerOwner ? ride.getOtp() : null)
                .startedAt(ride.getStartedAt())
                .completedAt(ride.getCompletedAt())
                .cancelledAt(ride.getCancelledAt())
                .cancellationReason(ride.getCancellationReason())
                .cancelledBy(ride.getCancelledBy())
                .rideType(ride.getRideType())
                .vehicleType(ride.getVehicleType())
                .createdAt(ride.getCreatedAt());

        if (driver != null) {
            builder.driverName(driver.getName())
                   .driverPhone(driver.getPhoneNumber())
                   .driverCarType(driver.getCarType())
                   .driverLicensePlate(driver.getLicensePlate())
                   .driverRating(driver.getRating() != null ? driver.getRating() : BigDecimal.valueOf(5.00))
                   .driverLatitude(driver.getCurrentLatitude())
                   .driverLongitude(driver.getCurrentLongitude());

            if (driver.getCurrentLatitude() != null && driver.getCurrentLongitude() != null
                    && ride.getPickupLatitude() != null && ride.getPickupLongitude() != null) {
                double lat1 = ride.getPickupLatitude();
                double lon1 = ride.getPickupLongitude();
                double lat2 = driver.getCurrentLatitude();
                double lon2 = driver.getCurrentLongitude();
                double dLat = Math.toRadians(lat2 - lat1);
                double dLon = Math.toRadians(lon2 - lon1);
                double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
                double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
                double distKm = 6371.0 * c;
                int mins = Math.max(1, (int) Math.round((distKm / 30.0) * 60));
                builder.driverEta(mins + " min (" + String.format("%.1f", distKm) + " km)");
            } else {
                builder.driverEta("3 min away");
            }
        }

        return builder.build();
    }
}
