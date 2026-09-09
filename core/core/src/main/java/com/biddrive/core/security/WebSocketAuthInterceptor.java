package com.biddrive.core.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * WebSocketAuthInterceptor - intercepts STOMP frames over the WebSocket connection.
 *
 * WHY a ChannelInterceptor and NOT an HTTP Filter?
 *   The standard JwtAuthFilter only intercepts the initial HTTP upgrade handshake on /ws.
 *   Standard browser WebSocket APIs do not permit custom HTTP headers (like Authorization)
 *   during that handshake.
 *   Instead, the STOMP protocol sends a CONNECT frame immediately after the socket opens,
 *   which allows arbitrary headers. We intercept that STOMP CONNECT frame here, validate
 *   the JWT, and bind the authenticated Principal to the WebSocket session.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public WebSocketAuthInterceptor(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 1. Extract Authorization header from STOMP connect headers
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            String token = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            } else {
                // Fallback: Check if client passed raw 'token' header
                token = accessor.getFirstNativeHeader("token");
            }

            // 2. Validate token and authenticate session Principal
            if (token != null && jwtUtil.isTokenValid(token)) {
                String email = jwtUtil.extractEmail(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Attach Principal to this WebSocket connection session
                accessor.setUser(authentication);
                System.out.println("🟢 [WebSocket Auth] Authenticated user: " + email + " (" + userDetails.getAuthorities() + ")");
            } else if (token != null) {
                System.err.println("🔴 [WebSocket Auth] Invalid or expired JWT passed during STOMP CONNECT.");
                throw new IllegalArgumentException("Invalid JWT token for WebSocket connection.");
            }
        }

        return message;
    }
}
