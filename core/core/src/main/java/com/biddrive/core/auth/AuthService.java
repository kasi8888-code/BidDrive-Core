package com.biddrive.core.auth;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Passenger;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.security.JwtUtil;
import com.biddrive.core.services.DriverLocationRedisService;
import com.biddrive.core.services.OtpService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final DriverRepository driverRepository;
    private final PassengerRepository passengerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final DriverLocationRedisService driverLocationRedisService;
    private final OtpService otpService;

    public AuthService(DriverRepository driverRepository,
                       PassengerRepository passengerRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuthenticationManager authenticationManager,
                       DriverLocationRedisService driverLocationRedisService,
                       OtpService otpService) {
        this.driverRepository = driverRepository;
        this.passengerRepository = passengerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.driverLocationRedisService = driverLocationRedisService;
        this.otpService = otpService;
    }

    public AuthResponse register(RegisterRequest request) {
        String role = request.getRole() != null ? request.getRole().toUpperCase().trim() : "";

        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            throw new RuntimeException("Phone number is required.");
        }
        String cleanPhone = request.getPhoneNumber().trim().replaceAll("[\\s-]", "");
        if (cleanPhone.startsWith("+91")) {
            cleanPhone = cleanPhone.substring(3);
        }
        if (!cleanPhone.matches("^[0-9]{10}$")) {
            throw new RuntimeException("Phone number must have exactly 10 digits.");
        }

        if ("DRIVER".equals(role)) {
            if (driverRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already registered as a Driver: " + request.getEmail());
            }

            if (driverRepository.existsByPhoneNumber(cleanPhone) || passengerRepository.existsByPhoneNumber(cleanPhone)) {
                throw new RuntimeException("Phone number is already registered: " + cleanPhone);
            }

            if (request.getLicensePlate() == null || request.getLicensePlate().isBlank()) {
                throw new RuntimeException("License plate is required for Driver registration.");
            }
            String cleanPlate = request.getLicensePlate().trim().replaceAll("[\\s-]", "").toUpperCase();
            if (cleanPlate.length() != 10) {
                throw new RuntimeException("Number plate must have exactly 10 characters (e.g. TN72AB1234).");
            }
            if (!cleanPlate.matches("^(?:[A-Z]{2}[0-9]{2}[A-Z]{2}(?!0000)[0-9]{4}|[0-9]{2}BH(?!0000)[0-9]{4}[A-Z]{2})$")) {
                throw new RuntimeException("Invalid number plate format. Must be a valid 10-character vehicle number (e.g. TN72AB1234).");
            }
            if (driverRepository.existsByLicensePlate(cleanPlate)) {
                throw new RuntimeException("License plate is already registered: " + cleanPlate);
            }

            if (request.getCarType() == null || request.getCarType().isBlank()) {
                throw new RuntimeException("Car type is required for Driver registration.");
            }

            Driver driver = new Driver();
            driver.setName(request.getName());
            driver.setEmail(request.getEmail().trim().toLowerCase());
            driver.setPhoneNumber(cleanPhone);
            driver.setLicensePlate(cleanPlate);
            driver.setCarType(request.getCarType());

            driver.setCurrentLatitude(13.0418);
            driver.setCurrentLongitude(80.2341);
            driver.setLastLocationUpdate(java.time.LocalDateTime.now());

            driver.setPassword(passwordEncoder.encode(request.getPassword()));
            driver.setRole("DRIVER");
            driver.setStatus("Available");
            driver.setIsVerified(false);

            Driver saved = driverRepository.save(driver);

            // Generate and send 6-digit verification code
            String otp = otpService.generateAndSendOtp(saved.getEmail(), saved.getName());

            return AuthResponse.builder()
                    .userId(saved.getId())
                    .email(saved.getEmail())
                    .role(saved.getRole())
                    .status("PENDING_VERIFICATION")
                    .message("Verification code sent to " + saved.getEmail() + ". Please verify to activate your account.")
                    .testOtp(otp)
                    .build();

        } else if ("PASSENGER".equals(role)) {
            if (passengerRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new RuntimeException("Email already registered as a Passenger: " + request.getEmail());
            }

            if (passengerRepository.existsByPhoneNumber(cleanPhone) || driverRepository.existsByPhoneNumber(cleanPhone)) {
                throw new RuntimeException("Phone number is already registered: " + cleanPhone);
            }

            Passenger passenger = new Passenger();
            passenger.setName(request.getName());
            passenger.setEmail(request.getEmail().trim().toLowerCase());
            passenger.setPhoneNumber(cleanPhone);
            passenger.setPassword(passwordEncoder.encode(request.getPassword()));
            passenger.setRole("PASSENGER");
            passenger.setIsVerified(false);

            Passenger saved = passengerRepository.save(passenger);

            // Generate and send 6-digit verification code
            String otp = otpService.generateAndSendOtp(saved.getEmail(), saved.getName());

            return AuthResponse.builder()
                    .userId(saved.getId())
                    .email(saved.getEmail())
                    .role(saved.getRole())
                    .status("PENDING_VERIFICATION")
                    .message("Verification code sent to " + saved.getEmail() + ". Please verify to activate your account.")
                    .testOtp(otp)
                    .build();

        } else {
            throw new RuntimeException("Invalid role. Must be 'DRIVER' or 'PASSENGER'.");
        }
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        otpService.validateOtp(cleanEmail, request.getOtp());

        var driverOpt = driverRepository.findByEmail(cleanEmail);
        if (driverOpt.isPresent()) {
            Driver d = driverOpt.get();
            d.setIsVerified(true);
            driverRepository.save(d);

            String token = jwtUtil.generateToken(d.getEmail(), d.getRole(), d.getId());
            return AuthResponse.builder()
                    .token(token)
                    .role(d.getRole())
                    .userId(d.getId())
                    .email(d.getEmail())
                    .status("VERIFIED")
                    .message("Email successfully verified! Welcome to BidDrive.")
                    .build();
        }

        var passengerOpt = passengerRepository.findByEmail(cleanEmail);
        if (passengerOpt.isPresent()) {
            Passenger p = passengerOpt.get();
            p.setIsVerified(true);
            passengerRepository.save(p);

            String token = jwtUtil.generateToken(p.getEmail(), p.getRole(), p.getId());
            return AuthResponse.builder()
                    .token(token)
                    .role(p.getRole())
                    .userId(p.getId())
                    .email(p.getEmail())
                    .status("VERIFIED")
                    .message("Email successfully verified! Welcome to BidDrive.")
                    .build();
        }

        throw new RuntimeException("User not found for email: " + cleanEmail);
    }

    public AuthResponse resendOtp(ResendOtpRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();
        String name = "User";

        var driverOpt = driverRepository.findByEmail(cleanEmail);
        if (driverOpt.isPresent()) {
            name = driverOpt.get().getName();
        } else {
            var passengerOpt = passengerRepository.findByEmail(cleanEmail);
            if (passengerOpt.isPresent()) {
                name = passengerOpt.get().getName();
            } else {
                throw new RuntimeException("No registered account found with email: " + cleanEmail);
            }
        }

        String newOtp = otpService.resendOtp(cleanEmail, name);
        return AuthResponse.builder()
                .email(cleanEmail)
                .status("PENDING_VERIFICATION")
                .message("A new 6-digit verification code has been dispatched to " + cleanEmail)
                .testOtp(newOtp)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().trim().toLowerCase(),
                        request.getPassword()
                )
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String email = userDetails.getUsername();

        String role;
        Integer userId;

        var driverOpt = driverRepository.findByEmail(email);
        if (driverOpt.isPresent()) {
            Driver d = driverOpt.get();
            if (Boolean.FALSE.equals(d.getIsVerified())) {
                String otp = otpService.generateAndSendOtp(d.getEmail(), d.getName());
                return AuthResponse.builder()
                        .email(d.getEmail())
                        .role(d.getRole())
                        .userId(d.getId())
                        .status("PENDING_VERIFICATION")
                        .message("Account email is not verified. A verification code has been sent to " + d.getEmail())
                        .testOtp(otp)
                        .build();
            }

            role = d.getRole();
            userId = d.getId();
            if ("Available".equalsIgnoreCase(d.getStatus()) || d.getStatus() == null) {
                double lat = d.getCurrentLatitude() != null ? d.getCurrentLatitude() : 13.0418;
                double lng = d.getCurrentLongitude() != null ? d.getCurrentLongitude() : 80.2341;
                try {
                    driverLocationRedisService.updateDriverLocation(d.getId(), lat, lng);
                } catch (Exception ignored) {}
            }
        } else {
            var passenger = passengerRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found after authentication - this should never happen."));
            if (Boolean.FALSE.equals(passenger.getIsVerified())) {
                String otp = otpService.generateAndSendOtp(passenger.getEmail(), passenger.getName());
                return AuthResponse.builder()
                        .email(passenger.getEmail())
                        .role(passenger.getRole())
                        .userId(passenger.getId())
                        .status("PENDING_VERIFICATION")
                        .message("Account email is not verified. A verification code has been sent to " + passenger.getEmail())
                        .testOtp(otp)
                        .build();
            }

            role = passenger.getRole();
            userId = passenger.getId();
        }

        String token = jwtUtil.generateToken(email, role, userId);
        return new AuthResponse(token, role, userId, email);
    }
}
