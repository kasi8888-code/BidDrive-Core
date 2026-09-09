package com.biddrive.core.controllers;

import com.biddrive.core.models.Ride;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.RideService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * RideController - secured ride management endpoints.
 *
 * POST /api/rides is PASSENGER-only:
 *   A driver should never be able to create a ride request - they respond to rides with bids.
 *   The authenticated passenger's ID is extracted from the JWT and set server-side,
 *   preventing a passenger from creating a ride on behalf of another passenger.
 *
 * GET endpoints are open to any authenticated user:
 *   Both drivers (to see available rides to bid on) and passengers (to see their own rides)
 *   need read access. Fine-grained filtering (show driver only their bids, passenger only
 *   their rides) is a Milestone 2 feature.
 */
@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;
    private final JwtUtil jwtUtil;

    public RideController(RideService rideService, JwtUtil jwtUtil) {
        this.rideService = rideService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * POST /api/rides
     * Requires: Authorization: Bearer <PASSENGER_TOKEN>
     *
     * The passengerId in the request body is OVERWRITTEN with the authenticated
     * passenger's ID from the JWT - same pattern as BidController.driverId.
     */
    @PostMapping
    @PreAuthorize("hasRole('PASSENGER')")
    public Ride createRide(
            @RequestBody Ride ride,
            @RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);

        // SECURITY: Overwrite whatever passengerId the client sent
        ride.setPassengerId(authenticatedPassengerId);

        return rideService.createRide(ride);
    }

    /** GET /api/rides - any authenticated user (drivers browse available rides) */
    @GetMapping
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }

    @GetMapping("/{id}")
    public Ride getRideById(@PathVariable Integer id) {
        return rideService.getRideById(id).orElse(null);
    }

    @GetMapping("/{id}/auction")
    public Map<Object, Object> getLiveAuction(@PathVariable Integer id) {
        return rideService.getLiveAuction(id);
    }

    /** DELETE /api/rides/{id} - PASSENGER only (they own the ride) */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PASSENGER')")
    public void deleteRide(
            @PathVariable Integer id,
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        Integer authenticatedPassengerId = jwtUtil.extractUserId(token);
        rideService.deleteRide(id, authenticatedPassengerId);
    }
}
