package com.biddrive.core.config;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * GlobalExceptionHandler - centralized error handling for the entire application.
 *
 * WHY centralize here instead of try/catch in every controller?
 *   Controllers should only know about the happy path. Exception handling is
 *   cross-cutting infrastructure. @RestControllerAdvice intercepts exceptions thrown
 *   from ANY @RestController before they reach the client, so we handle them once.
 *
 * WHY NOT let Spring Security handle 401/403 itself?
 *   By default, Spring Security returns its OWN error HTML pages for 401/403.
 *   Our API clients (Postman, mobile apps) expect JSON. By catching
 *   AuthenticationException and AccessDeniedException here, we return consistent
 *   JSON error bodies across ALL error scenarios.
 *
 *   NOTE: Spring Security's filter-level exceptions (e.g., thrown BEFORE reaching
 *   a controller) are NOT caught by @RestControllerAdvice. For those, we would
 *   need AuthenticationEntryPoint + AccessDeniedHandler beans wired into SecurityConfig.
 *   The exceptions caught here are thrown from within controller/service method bodies
 *   (e.g., @PreAuthorize violations -> AccessDeniedException, bad login -> BadCredentialsException).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @PreAuthorize violations (wrong role) -> 403 Forbidden.
     * Example: a DRIVER calls PUT /api/bids/{id}/accept (PASSENGER-only endpoint).
     *
     * WHY 403 and NOT 401?
     *   401 = "I don't know who you are" (unauthenticated).
     *   403 = "I know who you are, but you're not allowed to do this" (unauthorized).
     *   @PreAuthorize failures always happen AFTER authentication, so it's always 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Access Denied: You do not have permission to perform this action.");
        response.put("detail", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handles bad login credentials -> 401 Unauthorized.
     * Thrown by AuthenticationManager.authenticate() when email/password is wrong.
     *
     * WHY a generic message and NOT "wrong password" or "email not found"?
     *   Security best practice: never tell an attacker WHICH field is wrong.
     *   "Invalid email or password" gives nothing away - an attacker can't distinguish
     *   between "that email doesn't exist" and "that password is wrong."
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handles other Spring Security AuthenticationExceptions -> 401.
     * E.g., UsernameNotFoundException (user was deleted between token issue and now).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Authentication failed: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handles Bean Validation failures (@Valid on @RequestBody) -> 400.
     * Returns a map of { "fieldName": "error message" } for every failing field.
     *
     * WHY return per-field errors?
     *   API clients (React/mobile) use these to show field-level validation messages
     *   to the user (e.g., highlighting the "email" input with "Must be a valid email address").
     *   A single generic "bad request" forces the client to guess which field failed.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Handles our domain RuntimeExceptions -> 400.
     * E.g., "Ride not found", "Driver not available", "Email already registered".
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handles DB constraint violations -> 400.
     * E.g., duplicate unique email inserted directly at the DB level.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Integrity Violation: Duplicate entry or missing required field.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Catch-all for anything unexpected -> 500 Internal Server Error.
     * WHY 500 here and 400 for RuntimeException?
     *   RuntimeException is our intentional business-logic error.
     *   A raw Exception that slips through is unexpected - it warrants a 500.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
