package com.biddrive.core.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthFilter - intercepts every HTTP request and validates the JWT.
 *
 * WHY extend OncePerRequestFilter and NOT GenericFilterBean?
 *   OncePerRequestFilter guarantees this filter runs EXACTLY ONCE per request,
 *   even in async/forwarded scenarios. GenericFilterBean could run multiple times.
 *   Spring Security documentation recommends OncePerRequestFilter for auth filters.
 *
 * HOW the flow works (read this carefully):
 *   1. Client sends:  GET /api/rides   +   Authorization: Bearer eyJ...
 *   2. This filter extracts the token from the Authorization header.
 *   3. JwtUtil validates the token (signature + expiry).
 *   4. We load UserDetails from DB to get the authorities (roles).
 *   5. We create a UsernamePasswordAuthenticationToken and put it in the SecurityContext.
 *   6. SecurityContext now has an authenticated principal for the rest of this request.
 *   7. The request continues down the filter chain to the Controller.
 *
 * WHY do we need to call loadUserByUsername (DB hit) on EVERY request?
 *   Short answer: we need the GrantedAuthority list (roles) to build the Authentication object.
 *   Alternative: embed authorities in the JWT claims and skip the DB hit.
 *   We DO embed role in the JWT, but we still call UserDetailsService here so that
 *   if an admin revokes a user or changes their role, the change takes effect without
 *   waiting for token expiry. (You CAN skip this DB call for high-traffic scenarios
 *   by reading role directly from the JWT claims - a valid optimisation for later.)
 *
 * WHY check SecurityContextHolder.getContext().getAuthentication() == null first?
 *   If a previous filter already authenticated this request (e.g., in tests or chains),
 *   we must NOT overwrite it. This is the standard Spring Security guard.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Step 1: Read the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // If the header is missing or doesn't start with "Bearer ", skip this filter.
        // The request will hit the SecurityConfig rules and be rejected as 401 if
        // the endpoint requires authentication.
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: Extract the raw JWT (strip "Bearer " prefix, 7 chars)
        final String token = authHeader.substring(7);

        try {
            // Step 3: Validate token and extract the email (= subject)
            if (!jwtUtil.isTokenValid(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            final String email = jwtUtil.extractEmail(token);

            // Step 4: Only proceed if we have an email AND the SecurityContext is empty
            //         (not already authenticated by a previous filter)
            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // Step 5: Load UserDetails from DB - gets us the GrantedAuthority list
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Step 6: Build the Authentication token.
                //   - credentials = null  (we don't pass the password around after auth)
                //   - authorities = roles from UserDetails
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,      // principal (the UserDetails object)
                                null,             // credentials (null after authentication)
                                userDetails.getAuthorities()
                        );

                // Attach request metadata (IP address, session ID) to the auth token.
                // This is used by Spring Security's audit/logging subsystem.
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Step 7: Store in SecurityContext - request is now "authenticated"
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (JwtException e) {
            // Token is malformed, expired, or has wrong signature.
            // We don't set authentication -> Spring Security will return 401.
            // We do NOT throw here - we just let the request continue unauthenticated.
        }

        // Step 8: Pass to the next filter (or the controller if this is the last filter)
        filterChain.doFilter(request, response);
    }
}
