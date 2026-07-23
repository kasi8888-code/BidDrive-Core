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

    public RideService(RideRepository rideRepository, PassengerRepository passengerRepository) {
        this.rideRepository = rideRepository;
        this.passengerRepository = passengerRepository;
    }

    public List<Ride> getAllRides() {
        return rideRepository.findAll();
    }

    public Optional<Ride> getRideById(Integer id) {
        return rideRepository.findById(id);
    }

    public Ride createRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }
        return rideRepository.save(ride);
    }

    public Ride updateRide(Ride ride) {
        if (!passengerRepository.existsById(ride.getPassengerId())) {
            throw new RuntimeException("Passenger not found with id: " + ride.getPassengerId());
        }
        return rideRepository.save(ride);
    }

    public void deleteRide(Integer id) {
        rideRepository.deleteById(id);
    }
}