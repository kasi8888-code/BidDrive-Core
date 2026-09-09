package com.biddrive.core.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * RegisterRequest - the payload a client sends to POST /api/auth/register.
 *
 * WHY a dedicated DTO (Data Transfer Object) and NOT reuse Driver/Passenger entity?
 *   1. Entities are JPA-managed - you don't want Jackson trying to deserialise every
 *      field (including DB-managed fields like id, createdAt) from the HTTP request body.
 *   2. DTOs give us a layer to apply validation constraints WITHOUT polluting the entity.
 *   3. The registration form will always have a plain-text password field - you NEVER
 *      want a plain-text password field on a JPA entity (it would show up in queries,
 *      logs, toString() output via Lombok @Data, etc.).
 *
 * WHY @NotBlank and NOT @NotNull?
 *   @NotNull allows "" (empty string). @NotBlank rejects null AND empty/whitespace strings.
 *   Email "  " would pass @NotNull but fail @NotBlank - we want the stricter check.
 *
 * WHY @Size(min=8) on password?
 *   An 8-character minimum is the NIST SP 800-63B baseline recommendation.
 *   BCrypt max input is 72 bytes - practically you'd cap at 128 chars too.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    /**
     * "DRIVER" or "PASSENGER" - determines which table to save the record in
     * and which role gets embedded in the JWT.
     *
     * WHY accept role from the client and NOT infer it from the endpoint path?
     *   A single /api/auth/register endpoint keeps the API surface small.
     *   The AuthService validates that the value is exactly "DRIVER" or "PASSENGER"
     *   and throws an error for anything else, so there is no security risk.
     */
    @NotBlank(message = "Role is required (DRIVER or PASSENGER)")
    private String role;

    // Driver-only fields (nullable for passengers)
    private String licensePlate;
    private String carType;
}
