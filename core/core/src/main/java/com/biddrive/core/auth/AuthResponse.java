package com.biddrive.core.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * AuthResponse - what the server returns on successful login or registration.
 *
 * WHY return userId and role in the response body AND in the JWT?
 *   In the JWT, they're used server-side on every subsequent request (zero DB lookup).
 *   In the response body, they let the client (mobile app, React app) immediately
 *   know who they are and display the correct UI without parsing the JWT client-side.
 *   (Parsing a JWT client-side in JavaScript is fine but adds a dependency.)
 *
 * WHY NOT return the user's full profile here?
 *   The client can call GET /api/drivers/{id} or GET /api/passengers/{id}
 *   if they need more details. Auth response should be minimal.
 */
@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private Integer userId;
    private String email;
}
