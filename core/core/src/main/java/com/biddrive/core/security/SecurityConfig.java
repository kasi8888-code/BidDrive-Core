package com.biddrive.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * SecurityConfig - the central Spring Security configuration.
 *
 * WHY @EnableWebSecurity?
 *   Activates Spring Security's web-layer integration and registers the filter chain.
 *
 * WHY @EnableMethodSecurity?
 *   Enables @PreAuthorize on controller methods (e.g., hasRole('DRIVER')).
 *   Without this, @PreAuthorize annotations are silently ignored.
 *
 * WHY SessionCreationPolicy.STATELESS?
 *   JWT tokens are self-contained. The server never needs to store session state.
 *   STATELESS tells Spring Security: never create or consult an HttpSession.
 *
 * WHY disable CSRF?
 *   CSRF exploits browser cookie auto-attachment. We use Bearer tokens (not cookies),
 *   so browsers cannot auto-attach them. CSRF protection is unnecessary and would
 *   break REST clients (Postman, mobile apps).
 *
 * WHY BCryptPasswordEncoder?
 *   - Adaptive cost factor: increase rounds as hardware improves.
 *   - Built-in per-password salt: immune to rainbow table attacks.
 *   - Industry standard, battle-tested, Spring's own recommendation.
 *   NOT SHA-256 (fast = GPU-crackable), NOT MD5 (broken), NOT plain text.
 *
 * Route-level authorisation decisions:
 *   POST /api/auth/**           - PUBLIC (register and login, no token needed)
 *   GET  /api/rides/**          - Any authenticated user (drivers browse rides)
 *   POST /api/rides             - PASSENGER only (drivers don't create ride requests)
 *   DELETE /api/rides/**        - PASSENGER only
 *   GET  /api/bids/**           - Any authenticated user
 *   POST /api/bids              - DRIVER only (only drivers place bids)
 *   PUT  /api/bids/{id}/accept  - PASSENGER only (passengers choose their driver)
 *   Everything else             - Must be authenticated
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          UserDetailsServiceImpl userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    /**
     * The SecurityFilterChain bean defines all HTTP security rules.
     *
     * WHY addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)?
     *   Our JwtAuthFilter must populate the SecurityContext BEFORE Spring's built-in
     *   filters run. UsernamePasswordAuthenticationFilter handles form login; by inserting
     *   before it, our JWT-based authentication wins first.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/ws/**").permitAll()
                    .requestMatchers("/", "/index.html", "/test-auction.html", "/favicon.ico").permitAll()
                    .requestMatchers(HttpMethod.GET,    "/api/rides/**").authenticated()
                    .requestMatchers(HttpMethod.POST,   "/api/rides").hasRole("PASSENGER")
                    .requestMatchers(HttpMethod.PUT,    "/api/rides/**").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/rides/**").hasRole("PASSENGER")
                    .requestMatchers(HttpMethod.PUT,    "/api/drivers/**").hasRole("DRIVER")
                    .requestMatchers(HttpMethod.GET,    "/api/drivers/**").authenticated()
                    .requestMatchers(HttpMethod.GET,    "/api/bids/**").authenticated()
                    .requestMatchers(HttpMethod.POST,   "/api/bids").hasRole("DRIVER")
                    .requestMatchers(HttpMethod.PUT,    "/api/bids/{bidId}/accept").hasRole("PASSENGER")
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCryptPasswordEncoder bean.
     *
     * WHY a @Bean and not just "new BCryptPasswordEncoder()" inline?
     *   Declaring it as a Spring Bean lets the auto-configured DaoAuthenticationProvider
     *   pick it up automatically. If you use "new" locally, Spring Security falls back
     *   to NoOpPasswordEncoder (plain-text comparison) without warning.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager bean - exposed so AuthService.login() can call
     * authenticationManager.authenticate(...) to verify credentials.
     *
     * WHY from AuthenticationConfiguration and not built manually?
     *   AuthenticationConfiguration already wired our UserDetailsService and
     *   PasswordEncoder into a DaoAuthenticationProvider. We reuse that instead
     *   of duplicating the setup.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
