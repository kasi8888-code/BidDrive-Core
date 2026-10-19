package com.biddrive.core;

import com.biddrive.core.listeners.RedisExpirationListener;
import com.biddrive.core.services.AuctionRedisService;
import com.biddrive.core.services.DriverLocationRedisService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class CoreApplicationTests {

    @MockitoBean
    private LettuceConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedisExpirationListener redisExpirationListener;

    @MockitoBean
    private AuctionRedisService auctionRedisService;

    @MockitoBean
    private DriverLocationRedisService driverLocationRedisService;

    @Test
    void contextLoads() {
        // Confirms that Spring Boot starts up, Hibernate maps all tables,
        // Spring Security, controllers, and services assemble with zero errors.
    }
}
