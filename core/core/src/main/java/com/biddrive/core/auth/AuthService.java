package com.biddrive.core.auth;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Passenger;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * AuthService - handles user registration and login.
 *
 * WHY is AuthService in the 'auth' package and NOT in 'services'?
 *   Auth is cross-cutting infrastructure (deals with security, not business logic).
 *   Keeping it in its own package makes the boundary clear:
 *   'services' = domain logic (bids, rides, auctions), 'auth' = authentication.
 *
 * Registration flow:
 *   1. Validate that the role is DRIVER or PASSENGER.
 *   2. Check the appropriate table for email uniqueness.
 *   3. BCrypt-hash the plain-text password.
 *   4. Save the entity to PostgreSQL.
 *   5. Generate and return a JWT (auto-login after register is good UX).
 *
 * Login flow:
 *   1. Call authenticationManager.authenticate() - this internally:
 *      a) Calls UserDetailsServiceImpl.loadUserByUsername(email) -> fetches from DB
 *      b) Calls passwordEncoder.matches(raw, hashed) -> verifies password
 *      c) Throws BadCredentialsException if either check fails
 *   2. On success, extract the email and role from the returned Authentication.
 *   3. Look up the userId from the DB (needed for the JWT claim).
 *   4. Generate and return a JWT.
 *
 * WHY use AuthenticationManager.authenticate() for login instead of manual checking?
 *   Best practice: let Spring Security own the authentication decision. It:
 *   - Handles BadCredentialsException and UsernameNotFoundException uniformly
 *   - Records auth events for auditing/monitoring (if configured)
 *   - Gives you a single place to plug in MFA, account locking, etc. later
 *   If we did `if (passwordEncoder.matches(...))` manually, we'd bypass all of this.
 */
@Service
public class AuthService {

    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    public AuthService(DriverRepository driverRepository,
                       PassengerRepository passengerRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager) {
        this.driverRepository = driverRepository;
        this.passengerRepository = passengerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Register a new Driver or Passenger.
     *
     * WHY throw RuntimeException for duplicates and not a custom exception?
     *   RuntimeException is caught by our GlobalExceptionHandler and returned as 400 Bad Request.
     *   We'll add a more specific DuplicateEmailException in a later milestone when we
     *   want fine-grained HTTP status codes (409 Conflict). For now, 400 is acceptable.
     */
    public AuthResponse register(RegisterRequest request) {
        String role = request.getRole().toUpperCase().trim();

        if ("DRIVER".equals(role)) {
            // Guard: email uniqueness
            if (driverRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already registered as a Driver: " + request.getEmail());
            }

            // Driver-specific field validation
            if (request.getLicensePlate() == null || request.getLicensePlate().isBlank()) {
                throw new RuntimeException("License plate is required for Driver registration.");
            }
            if (request.getCarType() == null || request.getCarType().isBlank()) {
                throw new RuntimeException("Car type is required for Driver registration.");
            }

            // Build the entity
            Driver driver = new Driver();
            driver.setName(request.getName());
            driver.setEmail(request.getEmail());
            driver.setPhoneNumber(request.getPhoneNumber());
            driver.setLicensePlate(request.getLicensePlate());
            driver.setCarType(request.getCarType());
            // Hash the password - NEVER store plain text
            driver.setPassword(passwordEncoder.encode(request.getPassword()));
            driver.setRole("DRIVER");

            Driver saved = driverRepository.save(driver);

            // Generate a JWT immediately (auto-login after registration)
            String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getId());
            return new AuthResponse(token, saved.getRole(), saved.getId(), saved.getEmail());

        } else if ("PASSENGER".equals(role)) {
            if (passengerRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already registered as a Passenger: " + request.getEmail());
            }

            Passenger passenger = new Passenger();
            passenger.setName(request.getName());
            passenger.setEmail(request.getEmail());
            passenger.setPhoneNumber(request.getPhoneNumber());
            passenger.setPassword(passwordEncoder.encode(request.getPassword()));
            passenger.setRole("PASSENGER");

            Passenger saved = passengerRepository.save(passenger);

            String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getId());
            return new AuthResponse(token, saved.getRole(), saved.getId(), saved.getEmail());

        } else {
            throw new RuntimeException("Invalid role. Must be 'DRIVER' or 'PASSENGER'.");
        }
    }

    /**
     * Login an existing user.
     *
     * The authenticationManager.authenticate() call internally delegates to
     * UserDetailsServiceImpl.loadUserByUsername() + BCryptPasswordEncoder.matches().
     * If credentials are wrong, it throws BadCredentialsException -> 401.
     */
    public AuthResponse login(LoginRequest request) {
        // Throws BadCredentialsException if email or password is wrong
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Authentication succeeded - extract the UserDetails principal
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        // Determine role and userId by checking which table has this email
        // (We must hit the DB here because UserDetails doesn't store our custom userId field)
        String role;
        Integer userId;

        var driverOpt = driverRepository.findByEmail(email);
        if (driverOpt.isPresent()) {
            role = driverOpt.get().getRole();
            userId = driverOpt.get().getId();
        } else {
            var passenger = passengerRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found after authentication - this should never happen."));
            role = passenger.getRole();
            userId = passenger.getId();
        }

        String token = jwtUtil.generateToken(email, role, userId);
        return new AuthResponse(token, role, userId, email);
    }
}
