package com.biddrive.core.security;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Passenger;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.PassengerRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;

    public UserDetailsServiceImpl(DriverRepository driverRepository,
                                  PassengerRepository passengerRepository) {
        this.driverRepository = driverRepository;
        this.passengerRepository = passengerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        Optional<Driver> driverOpt = driverRepository.findByEmail(email);
        if (driverOpt.isPresent()) {
            Driver driver = driverOpt.get();
            return User.builder()
                    .username(driver.getEmail())
                    .password(driver.getPassword())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + driver.getRole())))
                    .build();
        }

        Optional<Passenger> passengerOpt = passengerRepository.findByEmail(email);
        if (passengerOpt.isPresent()) {
            Passenger passenger = passengerOpt.get();
            return User.builder()
                    .username(passenger.getEmail())
                    .password(passenger.getPassword())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + passenger.getRole())))
                    .build();
        }

        throw new UsernameNotFoundException("No user found with email: " + email);
    }
}
