package com.biddrive.core.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController - exposes the public-facing authentication endpoints.
 *
 * WHY @Valid on @RequestBody?
 *   This triggers Bean Validation (the @NotBlank, @Email, @Size constraints we put on
 *   RegisterRequest and LoginRequest). Without @Valid, those annotations are ignored.
 *   On validation failure, Spring throws MethodArgumentNotValidException -> 400 Bad Request
 *   with field-level error messages.
 *
 * WHY ResponseEntity<AuthResponse> and not just AuthResponse?
 *   ResponseEntity lets us explicitly control the HTTP status code.
 *   - Register -> 201 Created (a new resource was created in the DB)
 *   - Login    -> 200 OK     (no new resource, just authentication)
 *   Using @ResponseStatus annotations would work too, but ResponseEntity is more explicit.
 *
 * WHY NOT @PreAuthorize here?
 *   These endpoints are in SecurityConfig's permitAll() list - they're intentionally
 *   public. No auth token needed to register or login.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     *
     * Body (DRIVER):
     * {
     *   "name": "Ravi Kumar",
     *   "email": "ravi@example.com",
     *   "password": "securePass1",
     *   "phoneNumber": "9876543210",
     *   "role": "DRIVER",
     *   "licensePlate": "TN01AB1234",
     *   "carType": "Sedan"
     * }
     *
     * Body (PASSENGER):
     * {
     *   "name": "Priya Sharma",
     *   "email": "priya@example.com",
     *   "password": "myPassword9",
     *   "phoneNumber": "9123456789",
     *   "role": "PASSENGER"
     * }
     *
     * Response 201:
     * {
     *   "token": "eyJ...",
     *   "role": "DRIVER",
     *   "userId": 42,
     *   "email": "ravi@example.com"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     *
     * Body:
     * {
     *   "email": "ravi@example.com",
     *   "password": "securePass1"
     * }
     *
     * Response 200:
     * {
     *   "token": "eyJ...",
     *   "role": "DRIVER",
     *   "userId": 42,
     *   "email": "ravi@example.com"
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
