package com.biddrive.core.controllers;

import com.biddrive.core.models.Ride;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.RideService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;
    private final JwtUtil jwtUtil;
    private final com.biddrive.core.services.GeoDistanceService geoDistanceService;
    private final com.biddrive.core.repositories.DriverRepository driverRepository;

    public RideController(RideService rideService,
                          JwtUtil jwtUtil,
                          com.biddrive.core.services.GeoDistanceService geoDistanceService,
                          com.biddrive.core.repositories.DriverRepository driverRepository) {
        this.rideService = rideService;
        this.jwtUtil = jwtUtil;
        this.geoDistanceService = geoDistanceService;
        this.driverRepository = driverRepository;
    }

    @GetMapping("/estimate")
    public Map<String, Object> estimateRide(
            @RequestParam Double pickupLat,
            @RequestParam Double pickupLng,
            @RequestParam Double destinationLat,
            @RequestParam Double destinationLng) {
        double distanceKm = geoDistanceService.calculateDistanceKm(pickupLat, pickupLng, destinationLat, destinationLng);
        java.math.BigDecimal suggestedFare = geoDistanceService.calculateSuggestedBaseFare(distanceKm);
        return Map.of(
                "distanceKm", distanceKm,
                "suggestedBaseFare", suggestedFare
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public Ride createRide(
            @RequestBody Ride ride,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);

        ride.setPassengerId(authenticatedPassengerId);

        return rideService.createRide(ride);
    }

    @PostMapping("/instant")
    @PreAuthorize("hasRole('PASSENGER')")
    public Ride createInstantRide(
            @RequestBody Ride ride,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);

        ride.setPassengerId(authenticatedPassengerId);

        return rideService.createInstantRide(ride);
    }

    @PostMapping("/{id}/retry-auction")
    @PreAuthorize("hasRole('PASSENGER')")
    public com.biddrive.core.dtos.RideResponseDto retryAuction(
            @PathVariable Integer id,
            @RequestParam(required = false) BigDecimal newBaseFare,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer passengerId = jwtUtil.extractUserId(token);
        Ride ride = rideService.retryAuction(id, passengerId, newBaseFare);
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, passengerId, "PASSENGER");
    }

    @PostMapping("/{id}/convert-to-instant")
    @PreAuthorize("hasRole('PASSENGER')")
    public com.biddrive.core.dtos.RideResponseDto convertToInstant(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer passengerId = jwtUtil.extractUserId(token);
        Ride ride = rideService.convertToInstant(id, passengerId);
        com.biddrive.core.models.Driver driver = ride.getDriverId() != null
                ? driverRepository.findById(ride.getDriverId()).orElse(null) : null;
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driver, passengerId, "PASSENGER");
    }

    @GetMapping
    public List<com.biddrive.core.dtos.RideResponseDto> getAllRides(
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer userId = null;
        String role = null;
        String driverCarType = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userId = jwtUtil.extractUserId(token);
            role = jwtUtil.extractRole(token);

            if ("DRIVER".equalsIgnoreCase(role)) {
                driverCarType = driverRepository.findById(userId)
                        .map(com.biddrive.core.models.Driver::getCarType).orElse(null);
            }
        }
        final Integer finalUserId = userId;
        final String finalRole = role;
        return rideService.getRides(status, driverCarType).stream()
                .map(r -> {
                    com.biddrive.core.models.Driver driver = r.getDriverId() != null
                            ? driverRepository.findById(r.getDriverId()).orElse(null) : null;
                    com.biddrive.core.dtos.RideResponseDto dto = com.biddrive.core.dtos.RideResponseDto.fromEntity(r, driver, finalUserId, finalRole);
                    if ("AUCTION".equalsIgnoreCase(r.getRideType()) && "INITIATED".equalsIgnoreCase(r.getStatus())) {
                        dto.setRemainingSeconds(rideService.getRemainingSeconds(r.getId()));
                    }
                    return dto;
                })
                .toList();
    }

    @GetMapping("/{id}")
    public com.biddrive.core.dtos.RideResponseDto getRideById(
            @PathVariable Integer id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Integer userId = null;
        String role = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userId = jwtUtil.extractUserId(token);
            role = jwtUtil.extractRole(token);
        }
        final Integer finalUserId = userId;
        final String finalRole = role;
        return rideService.getRideById(id)
                .map(r -> {
                    com.biddrive.core.models.Driver driver = r.getDriverId() != null
                            ? driverRepository.findById(r.getDriverId()).orElse(null) : null;
                    return com.biddrive.core.dtos.RideResponseDto.fromEntity(r, driver, finalUserId, finalRole);
                })
                .orElse(null);
    }

    @GetMapping("/{id}/auction")
    public Map<Object, Object> getLiveAuction(@PathVariable Integer id) {
        return rideService.getLiveAuction(id);
    }

    @PostMapping("/{id}/simulate-bids")
    public List<com.biddrive.core.models.Bid> simulateBids(@PathVariable Integer id) {
        return rideService.simulateBids(id);
    }

    @PostMapping("/{id}/simulate-driver-step")
    public com.biddrive.core.dtos.RideResponseDto simulateDriverStep(
            @PathVariable Integer id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Ride ride = rideService.simulateDriverStep(id);
        com.biddrive.core.models.Driver driver = ride.getDriverId() != null
                ? driverRepository.findById(ride.getDriverId()).orElse(null) : null;
        Integer userId = null;
        String role = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            userId = jwtUtil.extractUserId(token);
            role = jwtUtil.extractRole(token);
        }
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driver, userId, role);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PASSENGER')")
    public void deleteRide(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);
        rideService.deleteRide(id, authenticatedPassengerId);
    }

    @PutMapping("/{id}/arriving")
    @PreAuthorize("hasRole('DRIVER')")
    public com.biddrive.core.dtos.RideResponseDto markArriving(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer driverId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        Ride ride = rideService.markDriverArriving(id, driverId);
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driverId, role);
    }

    @PutMapping("/{id}/arrived")
    @PreAuthorize("hasRole('DRIVER')")
    public com.biddrive.core.dtos.RideResponseDto markArrived(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer driverId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        Ride ride = rideService.markDriverArrived(id, driverId);
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driverId, role);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('DRIVER')")
    public com.biddrive.core.dtos.RideResponseDto startTrip(
            @PathVariable Integer id,
            @jakarta.validation.Valid @RequestBody com.biddrive.core.dtos.StartRideRequestDto request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer driverId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        Ride ride = rideService.startTrip(id, driverId, request.getOtp());
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driverId, role);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasRole('DRIVER')")
    public com.biddrive.core.dtos.RideResponseDto completeRide(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer driverId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        Ride ride = rideService.completeRide(id, driverId);
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driverId, role);
    }

    @PutMapping("/{id}/cancel")
    public com.biddrive.core.dtos.RideResponseDto cancelRide(
            @PathVariable Integer id,
            @RequestBody(required = false) com.biddrive.core.dtos.CancelRideRequestDto request,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer userId = jwtUtil.extractUserId(token);
        String role = jwtUtil.extractRole(token);
        String reason = (request != null) ? request.getCancellationReason() : null;
        Ride ride = rideService.cancelRide(id, userId, role, reason);
        com.biddrive.core.models.Driver driver = ride.getDriverId() != null
                ? driverRepository.findById(ride.getDriverId()).orElse(null) : null;
        return com.biddrive.core.dtos.RideResponseDto.fromEntity(ride, driver, userId, role);
    }
}
