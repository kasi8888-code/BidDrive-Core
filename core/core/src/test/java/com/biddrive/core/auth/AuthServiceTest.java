package com.biddrive.core.auth;

import com.biddrive.core.models.Driver;
import com.biddrive.core.models.Passenger;
import com.biddrive.core.repositories.DriverRepository;
import com.biddrive.core.repositories.PassengerRepository;
import com.biddrive.core.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("Driver Registration: Hashes password, saves driver, returns token")
    void testRegisterDriverSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Rajesh");
        request.setEmail("rajesh@example.com");
        request.setPassword("plainPass123");
        request.setPhoneNumber("9876543210");
        request.setRole("DRIVER");
        request.setLicensePlate("KA01AB1234");
        request.setCarType("Sedan");

        when(driverRepository.findByEmail("rajesh@example.com")).thenReturn(Optional.empty());
        when(driverRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(passengerRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(driverRepository.existsByLicensePlate("KA01AB1234")).thenReturn(false);
        when(passwordEncoder.encode("plainPass123")).thenReturn("$2a$10$hashedPassword");

        Driver savedDriver = new Driver();
        savedDriver.setId(5);
        savedDriver.setEmail("rajesh@example.com");
        savedDriver.setRole("DRIVER");
        when(driverRepository.save(any(Driver.class))).thenReturn(savedDriver);
        when(jwtUtil.generateToken("rajesh@example.com", "DRIVER", 5)).thenReturn("mock.jwt.token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token", response.getToken());
        assertEquals("DRIVER", response.getRole());
        assertEquals(5, response.getUserId());
        assertEquals("rajesh@example.com", response.getEmail());

        verify(driverRepository, times(1)).save(any(Driver.class));
    }

    @Test
    @DisplayName("Passenger Registration: Hashes password, saves passenger, returns token")
    void testRegisterPassengerSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Anita");
        request.setEmail("anita@example.com");
        request.setPassword("anitaPass99");
        request.setPhoneNumber("9123456789");
        request.setRole("PASSENGER");

        when(passengerRepository.findByEmail("anita@example.com")).thenReturn(Optional.empty());
        when(passengerRepository.existsByPhoneNumber("9123456789")).thenReturn(false);
        when(driverRepository.existsByPhoneNumber("9123456789")).thenReturn(false);
        when(passwordEncoder.encode("anitaPass99")).thenReturn("$2a$10$anitaHashed");

        Passenger savedPassenger = new Passenger();
        savedPassenger.setId(12);
        savedPassenger.setEmail("anita@example.com");
        savedPassenger.setRole("PASSENGER");
        when(passengerRepository.save(any(Passenger.class))).thenReturn(savedPassenger);
        when(jwtUtil.generateToken("anita@example.com", "PASSENGER", 12)).thenReturn("mock.jwt.token.passenger");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock.jwt.token.passenger", response.getToken());
        assertEquals("PASSENGER", response.getRole());
        assertEquals(12, response.getUserId());
    }

    @Test
    @DisplayName("Duplicate Email Rejection: Throws exception when registering existing driver email")
    void testRegisterDuplicateDriverEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPhoneNumber("9876543210");
        request.setRole("DRIVER");

        when(driverRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(new Driver()));

        assertThrows(RuntimeException.class, () -> authService.register(request));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Invalid License Plate Length Rejection: Rejects plate with != 10 chars (e.g. tn720000000)")
    void testRegisterDriverInvalidLicensePlateLength() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Suresh");
        request.setEmail("suresh@example.com");
        request.setPassword("password123");
        request.setPhoneNumber("9876543210");
        request.setRole("DRIVER");
        request.setLicensePlate("tn720000000"); // 11 characters
        request.setCarType("Sedan");

        when(driverRepository.findByEmail("suresh@example.com")).thenReturn(Optional.empty());
        when(driverRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(passengerRepository.existsByPhoneNumber("9876543210")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Number plate must have exactly 10 characters"));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Invalid License Plate Format Rejection: Rejects 10-char plate with missing series letters (e.g. tn72006565)")
    void testRegisterDriverInvalidLicensePlateFormat() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Suresh");
        request.setEmail("suresh@example.com");
        request.setPassword("password123");
        request.setPhoneNumber("9876543210");
        request.setRole("DRIVER");
        request.setLicensePlate("tn72006565"); // 10 chars, but '00' is invalid series
        request.setCarType("Sedan");

        when(driverRepository.findByEmail("suresh@example.com")).thenReturn(Optional.empty());
        when(driverRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(passengerRepository.existsByPhoneNumber("9876543210")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Invalid number plate format"));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Duplicate License Plate Rejection: Rejects plate already registered")
    void testRegisterDriverDuplicateLicensePlate() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Suresh");
        request.setEmail("suresh@example.com");
        request.setPassword("password123");
        request.setPhoneNumber("9876543210");
        request.setRole("DRIVER");
        request.setLicensePlate("TN72AB1234");
        request.setCarType("Sedan");

        when(driverRepository.findByEmail("suresh@example.com")).thenReturn(Optional.empty());
        when(driverRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(passengerRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(driverRepository.existsByLicensePlate("TN72AB1234")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("License plate is already registered"));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Invalid Phone Number Rejection: Rejects phone with != 10 digits")
    void testRegisterInvalidPhoneNumber() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ramesh");
        request.setEmail("ramesh@example.com");
        request.setPassword("password123");
        request.setPhoneNumber("98765"); // 5 digits
        request.setRole("DRIVER");

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Phone number must have exactly 10 digits"));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Duplicate Phone Number Rejection: Rejects phone already registered")
    void testRegisterDuplicatePhoneNumber() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ramesh");
        request.setEmail("ramesh@example.com");
        request.setPassword("password123");
        request.setPhoneNumber("9876543210");
        request.setRole("DRIVER");

        when(driverRepository.findByEmail("ramesh@example.com")).thenReturn(Optional.empty());
        when(driverRepository.existsByPhoneNumber("9876543210")).thenReturn(true);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Phone number is already registered"));
        verify(driverRepository, never()).save(any(Driver.class));
    }

    @Test
    @DisplayName("Login Success: Authenticates credentials, extracts user info, returns token")
    void testLoginSuccess() {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("driver@example.com");
        loginRequest.setPassword("secretPass");

        UserDetails userDetails = new User("driver@example.com", "secretPass", Collections.emptyList());
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);

        Driver driver = new Driver();
        driver.setId(7);
        driver.setEmail("driver@example.com");
        driver.setRole("DRIVER");
        when(driverRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(driver));
        when(jwtUtil.generateToken("driver@example.com", "DRIVER", 7)).thenReturn("valid.login.jwt");

        AuthResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("valid.login.jwt", response.getToken());
        assertEquals("DRIVER", response.getRole());
        assertEquals(7, response.getUserId());
    }
}
