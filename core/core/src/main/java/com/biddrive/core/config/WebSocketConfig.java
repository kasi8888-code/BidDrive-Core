package com.biddrive.core.config;

import com.biddrive.core.security.WebSocketAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocketConfig sets up the STOMP message broker and connection endpoints.
 *
 * Architecture:
 *   - /ws : Endpoint where clients initiate WebSocket/SockJS connection.
 *   - /topic : Public broadcast topics (e.g., /topic/auction/{rideId}, /topic/rides).
 *   - /queue : Targeted user queues (e.g., /user/queue/notifications).
 *   - /app   : Application-handled messages sent from clients to backend.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    public WebSocketConfig(WebSocketAuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Simple in-memory message broker routing /topic and /queue messages
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix for client-to-server destination mappings
        config.setApplicationDestinationPrefixes("/app");
        // Prefix used to send private messages to a single authenticated user
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 1. SockJS enabled endpoint (for browser-based web apps)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        // 2. Pure WebSocket endpoint (for mobile Flutter/React Native/iOS apps)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Attach JWT authentication interceptor for STOMP CONNECT frame
        registration.interceptors(authInterceptor);
    }
}
