package com.biddrive.core.controllers;

import com.biddrive.core.models.Passenger;
import com.biddrive.core.services.PassengerService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/passengers")
public class PassengerController {

    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @GetMapping
    public List<Passenger> getAllPassengers() {
        return passengerService.getAllPassengers();
    }

    @GetMapping("/{id}")
    public Passenger getPassengerById(@PathVariable Integer id) {
        return passengerService.getPassengerById(id).orElse(null);
    }
}
