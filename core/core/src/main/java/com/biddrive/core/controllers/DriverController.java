package com.biddrive.core.controllers;

import com.biddrive.core.models.Driver;
import com.biddrive.core.services.DriverService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/drivers")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping
    public List<Driver> getAllDrivers() {
        return driverService.getAllDrivers();
    }

    @GetMapping("/{id}")
    public Driver getDriverById(@PathVariable Integer id) {
        return driverService.getDriverById(id).orElse(null);
    }
}
