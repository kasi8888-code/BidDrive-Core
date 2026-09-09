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

/**
 * UserDetailsServiceImpl - the bridge between Spring Security and our database.
 *
 * WHY does this class exist?
 *   Spring Security's AuthenticationManager does NOT know about PostgreSQL.
 *   It only knows about UserDetails objects (username, password, authorities).
 *   We implement UserDetailsService so Spring Security can delegate the DB lookup to us.
 *
 * WHY implement UserDetailsService and NOT AuthenticationProvider directly?
 *   UserDetailsService is the simpler contract - just "give me a UserDetails for this username."
 *   AuthenticationProvider is lower level (you handle password comparison too).
 *   When you give Spring Security a UserDetailsService + a PasswordEncoder bean,
 *   it automatically wires up a DaoAuthenticationProvider that handles the password
 *   verification for you. Less code, same result.
 *
 * WHY check both Driver and Passenger tables?
 *   Email is globally unique across both user types in BidDrive.
 *   A driver and passenger CANNOT share the same email.
 *   We check Driver first, then Passenger. If neither has the email, authentication fails.
 *
 * WHY use Spring's built-in User.builder() and NOT our own class?
 *   The User class (org.springframework.security.core.userdetails.User) is a production-grade
 *   implementation of UserDetails with all the flag handling (accountNonExpired, etc.) built in.
 *   Building our own UserDetails just adds boilerplate.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;

    public UserDetailsServiceImpl(DriverRepository driverRepository,
                                  PassengerRepository passengerRepository) {
        this.driverRepository = driverRepository;
        this.passengerRepository = passengerRepository;
    }

    /**
     * Spring Security calls this with the value the client typed as "username."
     * In BidDrive, the "username" is the email address.
     *
     * WHY "ROLE_" prefix on the authority?
     *   Spring Security's @PreAuthorize("hasRole('DRIVER')") and .hasRole("DRIVER") in
     *   SecurityFilterChain BOTH automatically prepend "ROLE_" before comparing.
     *   So if you store "DRIVER" in the DB, you must register "ROLE_DRIVER" as the authority,
     *   otherwise hasRole() will never match.
     *   Alternatively you can use hasAuthority("DRIVER") without the prefix. We follow the
     *   Spring convention (ROLE_ prefix) because it's what every other Spring project expects.
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // Try Driver table first
        Optional<Driver> driverOpt = driverRepository.findByEmail(email);
        if (driverOpt.isPresent()) {
            Driver driver = driverOpt.get();
            return User.builder()
                    .username(driver.getEmail())
                    .password(driver.getPassword())  // already BCrypt-hashed in DB
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + driver.getRole())))
                    .build();
        }

        // Try Passenger table
        Optional<Passenger> passengerOpt = passengerRepository.findByEmail(email);
        if (passengerOpt.isPresent()) {
            Passenger passenger = passengerOpt.get();
            return User.builder()
                    .username(passenger.getEmail())
                    .password(passenger.getPassword())  // already BCrypt-hashed in DB
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + passenger.getRole())))
                    .build();
        }

        // Neither table has this email
        throw new UsernameNotFoundException("No user found with email: " + email);
    }
}
