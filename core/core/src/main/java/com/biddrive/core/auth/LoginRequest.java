package com.biddrive.core.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * LoginRequest - the payload a client sends to POST /api/auth/login.
 *
 * WHY just email and password?
 *   Login is purely credential verification. Role is NOT sent by the client -
 *   the server looks up the user from the DB (via UserDetailsService) and
 *   reads the role from the stored record. This prevents role spoofing
 *   (e.g., a driver claiming to be a passenger to get a different token).
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
