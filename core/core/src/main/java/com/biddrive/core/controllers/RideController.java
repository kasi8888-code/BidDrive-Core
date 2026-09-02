package com.biddrive.core.controllers;

import com.biddrive.core.models.Ride;
import com.biddrive.core.services.RideService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rides")
public class RideController {

    private final RideService rideService;

    public RideController(RideService rideService) {
        this.rideService = rideService;
    }

    @PostMapping
    public Ride createRide(@RequestBody Ride ride) {
        return rideService.createRide(ride);
    }

    @GetMapping
    public List<Ride> getAllRides() {
        return rideService.getAllRides();
    }

    @GetMapping("/{id}")
    public Ride getRideById(@PathVariable Integer id) {
        return rideService.getRideById(id).orElse(null);
    }

    @GetMapping("/{id}/auction")
    public java.util.Map<Object, Object> getLiveAuction(@PathVariable Integer id) {
        return rideService.getLiveAuction(id);
    }

    @DeleteMapping("/{id}")
    public void deleteRide(@PathVariable Integer id) {
        rideService.deleteRide(id);
    }

}
