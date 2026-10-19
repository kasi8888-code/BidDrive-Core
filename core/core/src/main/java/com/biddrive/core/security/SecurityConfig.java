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

                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .requestMatchers(HttpMethod.GET,    "/api/rides/**").authenticated()

                    .requestMatchers(HttpMethod.POST,   "/api/rides/instant").hasRole("PASSENGER")
                    .requestMatchers(HttpMethod.POST,   "/api/rides").hasRole("PASSENGER")
                    .requestMatchers(HttpMethod.PUT,    "/api/rides/**").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/rides/**").hasRole("PASSENGER")
                    .requestMatchers(HttpMethod.PUT,    "/api/drivers/**").hasRole("DRIVER")
                    .requestMatchers(HttpMethod.GET,    "/api/drivers/**").authenticated()
                    .requestMatchers(HttpMethod.GET,    "/api/bids/**").authenticated()
                    .requestMatchers(HttpMethod.POST,   "/api/bids").hasRole("DRIVER")
                    .requestMatchers(HttpMethod.PUT,    "/api/bids/{bidId}/accept").hasRole("PASSENGER")

                    .requestMatchers(HttpMethod.POST,   "/api/ratings").hasRole("PASSENGER")
                    .requestMatchers(HttpMethod.GET,    "/api/ratings/**").authenticated()

                    .requestMatchers(HttpMethod.GET,    "/api/wallet/fare-info").permitAll()
                    .requestMatchers(HttpMethod.GET,    "/api/wallet/**").hasRole("DRIVER")
                    .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @org.springframework.beans.factory.annotation.Value("${bcrypt.strength:4}")
    private int bcryptStrength;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(bcryptStrength);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
