package com.biddrive.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JwtUtil - the single source of truth for all JWT operations.
 *
 * WHY a separate @Component for this?
 *   Because JWT logic (generate, validate, extract claims) is pure infrastructure -
 *   it has nothing to do with business logic (bids, rides, etc.).
 *   Keeping it isolated makes it trivially testable and swappable.
 *
 * WHY JJWT 0.12.x and NOT the older 0.9.x you see in 99% of tutorials?
 *   - 0.9.x uses deprecated `Keys.hmacShaKeyFor(secret.getBytes())` and `Jwts.parser()`
 *   - 0.12.x uses the fluent builder API: `Jwts.builder()`, `Jwts.parser().verifyWith(key).build()`
 *   - 0.12.x gives compile-time safety - the old API silently accepted weak keys.
 *   - 0.12.x is the current maintained release.
 *
 * WHY HMAC-SHA256 (HS256) and NOT RSA (RS256)?
 *   - HS256 uses a SINGLE shared secret to both sign and verify - perfect for a monolith
 *     where the same service that issues tokens also verifies them.
 *   - RS256 uses a private key to sign and a public key to verify - necessary only when
 *     DIFFERENT services (e.g., an Auth Server + multiple microservices) must verify tokens
 *     WITHOUT sharing a secret. We're a monolith today, so HS256 is correct.
 *   - HS256 is faster and simpler. RS256 adds overhead we don't need yet.
 */
@Component
public class JwtUtil {

    // Loaded from application.properties at startup
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Build a SecretKey from the plain-text secret stored in application.properties.
     *
     * WHY SecretKey and not a raw String?
     *   JJWT 0.12.x's signing methods require a javax.crypto.SecretKey object, not a String.
     *   Keys.hmacShaKeyFor() converts our UTF-8 bytes into a proper HMAC key object.
     *
     * WHY call this every time instead of caching it as a field?
     *   Because @Value injection happens AFTER the constructor runs. If we set a field in
     *   the constructor, `secret` would still be null. A private method called lazily (or
     *   in a @PostConstruct) is the clean solution.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a signed JWT token for a successfully authenticated user.
     *
     * Claims we embed:
     *   - sub  (subject)  : the user's email - acts as the unique identifier
     *   - role            : "DRIVER" or "PASSENGER" - stored so the filter can
     *                       reconstruct the GrantedAuthority without a DB call
     *   - userId          : the DB primary key - needed so BidController can enforce
     *                       "driver can only bid as themselves"
     *   - iat  (issued at): timestamp, auto-set by JJWT
     *   - exp  (expiry)   : now + expirationMs
     *
     * WHY put userId in the token?
     *   When a driver calls POST /api/bids, we need to know WHO is calling.
     *   We could look up the DB every request (slow), or we can embed the ID
     *   in the token once and read it cheaply from the JWT on every request (fast).
     */
    public String generateToken(String email, String role, Integer userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        extraClaims.put("userId", userId);

        return Jwts.builder()
                .claims(extraClaims)          // custom claims go FIRST in 0.12.x
                .subject(email)               // standard "sub" claim
                .issuedAt(new Date())         // standard "iat" claim
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // "exp"
                .signWith(getSigningKey())    // HMAC-SHA256 by default for SecretKey
                .compact();                   // serialise to the Base64URL dot-separated string
    }

    /**
     * Parse and validate the token, then return all claims.
     * Throws JwtException (ExpiredJwtException, MalformedJwtException, etc.) on failure.
     *
     * WHY return Claims and not individual fields?
     *   One parse call, callers extract what they need. Avoids parsing the token 3 times.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())  // set the key used for HMAC verification
                .build()
                .parseSignedClaims(token)     // throws if expired, tampered, or wrong key
                .getPayload();                // returns the Claims map
    }

    /** Extract the email (= subject) from a valid token. */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Extract the role claim ("DRIVER" or "PASSENGER") from a valid token. */
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    /** Extract the DB userId from a valid token. */
    public Integer extractUserId(String token) {
        return extractAllClaims(token).get("userId", Integer.class);
    }

    /**
     * Returns true if the token is structurally valid AND not expired.
     * We wrap extractAllClaims in try/catch: any exception means invalid.
     */
    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token); // will throw if expired or tampered
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
