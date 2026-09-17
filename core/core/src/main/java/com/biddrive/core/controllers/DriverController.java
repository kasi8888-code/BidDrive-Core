package com.biddrive.core.controllers;

import com.biddrive.core.dtos.DriverLocationUpdateDto;
import com.biddrive.core.dtos.NearbyDriverDto;
import com.biddrive.core.models.Driver;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.DriverService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;
    private final JwtUtil jwtUtil;

    public DriverController(DriverService driverService, JwtUtil jwtUtil) {
        this.driverService = driverService;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping
    public List<Driver> getAllDrivers() {
        return driverService.getAllDrivers();
    }

    @GetMapping("/{id}")
    public Driver getDriverById(@PathVariable Integer id) {
        return driverService.getDriverById(id).orElse(null);
    }

    /**
     * PUT /api/drivers/location
     * Ingests real-time GPS coordinates for the authenticated driver.
     * Uses JWT to securely resolve driverId (preventing driver impersonation).
     */
    @PutMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    public Driver updateLocation(
            @Valid @RequestBody DriverLocationUpdateDto dto,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedDriverId = jwtUtil.extractUserId(token);
        return driverService.updateDriverLocation(authenticatedDriverId, dto.getLatitude(), dto.getLongitude());
    }

    /**
     * PUT /api/drivers/status
     * Updates driver availability status (e.g. 'Available', 'BUSY', 'Offline').
     */
    @PutMapping("/status")
    @PreAuthorize("hasRole('DRIVER')")
    public Driver updateStatus(
            @RequestParam(defaultValue = "Available") String status,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedDriverId = jwtUtil.extractUserId(token);
        return driverService.updateDriverStatus(authenticatedDriverId, status);
    }

    /**
     * GET /api/drivers/nearby
     * Discovers active drivers within a radius (default: 5.0 km) of the specified coordinates.
     */
    @GetMapping("/nearby")
    public List<NearbyDriverDto> getNearbyDrivers(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false, defaultValue = "5.0") Double radiusKm) {
        return driverService.findNearbyDrivers(latitude, longitude, radiusKm);
    }
}
