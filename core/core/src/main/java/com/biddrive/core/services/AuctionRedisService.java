package com.biddrive.core.services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuctionRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String KEY_PREFIX = "auction:ride:";
    private static final long TTL_SECONDS = 300; // 300 Seconds (5 Minutes) Auction Window for Postman testing

    public AuctionRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Initializes a 60-second ephemeral auction in Redis.
     */
    public void initAuction(Integer rideId, BigDecimal baseFare) {
        String key = KEY_PREFIX + rideId;

        Map<String, Object> auctionData = new HashMap<>();
        auctionData.put("rideId", String.valueOf(rideId));
        auctionData.put("lowestBid", baseFare.toString());
        auctionData.put("lowestDriverId", "NONE");
        auctionData.put("status", "ACTIVE");


        // Put all fields into Redis Hash
        redisTemplate.opsForHash().putAll(key, auctionData);

        // Set 60-second Time-To-Live (TTL)
        redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Retrieves current auction details from Redis.
     */
    public Map<Object, Object> getAuction(Integer rideId) {
        String key = KEY_PREFIX + rideId;
        return redisTemplate.opsForHash().entries(key);
    }

    /**
     * Checks if auction key is still active in Redis (has not expired).
     */
    public boolean isAuctionActive(Integer rideId) {
        String key = KEY_PREFIX + rideId;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
    /**
     * Atomically compares and submits a new bid in Redis.
     * Only accepts the bid if it is strictly lower than the current lowest bid.
     */
    public synchronized Map<String, Object> submitLiveBid(Integer rideId, Integer driverId, BigDecimal bidAmount) {
        String key = KEY_PREFIX + rideId;

        // 1. Check if auction key is still active (within the TTL window)
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new RuntimeException("Auction has expired or is not active.");
        }

        // 2. Fetch current lowest bid from Redis
        Object currentLowestBidObj = redisTemplate.opsForHash().get(key, "lowestBid");
        if (currentLowestBidObj == null) {
            throw new RuntimeException("Auction data is invalid or expired.");
        }

        BigDecimal currentLowestBid = new BigDecimal(currentLowestBidObj.toString());

        // 3. Reject if new bid is greater than or equal to current lowest bid
        if (bidAmount.compareTo(currentLowestBid) >= 0) {
            throw new RuntimeException("Bid rejected! Your bid (₹" + bidAmount + ") must be lower than the current lowest bid of ₹" + currentLowestBid + ".");
        }

        // 4. Update the new lowest bid and winning driver in Redis RAM
        redisTemplate.opsForHash().put(key, "lowestBid", bidAmount.toString());
        redisTemplate.opsForHash().put(key, "lowestDriverId", String.valueOf(driverId));

        Map<String, Object> result = new HashMap<>();
        result.put("rideId", rideId);
        result.put("newLowestBid", bidAmount.toString());
        result.put("lowestDriverId", String.valueOf(driverId));
        result.put("status", "ACCEPTED_AS_LOWEST");

        return result;
    }
}

