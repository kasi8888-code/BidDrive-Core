package com.biddrive.core.services;

import com.biddrive.core.models.Ride;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.repositories.RideRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RideService {

    private final RideRepository rideRepository;
    private final PassengerRepository passengerRepository;
    private final AuctionRedisService auctionRedisService;

    public RideService(RideRepository rideRepository, 
                       PassengerRepository passengerRepository,
                       AuctionRedisService auctionRedisService) {
        this.rideRepository = rideRepository;
        this.passengerRepository = passengerRepository;
        this.auctionRedisService = auctionRedisService;
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Optional<Ride> getRideById(Integer id) {
        return rideRepository.findById(id);
    }

    public java.util.Map<Object, Object> getLiveAuction(Integer rideId) {
        return auctionRedisService.getAuction(rideId);
    }


    public Ride createRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }

        // 1. Save to SQL
        Ride savedRide = rideRepository.save(ride);

        // 2. Instantiate 15-second Ephemeral Auction in Redis
        auctionRedisService.initAuction(savedRide.getId(), savedRide.getBaseFare());

        return savedRide;
    }


    public Ride updateRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }
        return rideRepository.save(ride);
    }

    public void deleteRide(Integer id, Integer authenticatedPassengerId) {
        Ride ride = rideRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ride not found with id: " + id));

        if (!ride.getPassengerId().equals(authenticatedPassengerId)) {
            throw new RuntimeException("Access Denied: You do not own this ride.");
        }

        rideRepository.deleteById(id);
    }
}
