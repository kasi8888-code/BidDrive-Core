package com.biddrive.core.services;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class AuctionRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String KEY_PREFIX = "auction:ride:";

    @org.springframework.beans.factory.annotation.Value("${auction.ttl-seconds:90}")
    private long ttlSeconds;

    private static final DefaultRedisScript<String> SUBMIT_BID_SCRIPT;
    static {
        String lua =
            "local key = KEYS[1] " +
            "local newBid = tonumber(ARGV[1]) " +
            "local driverId = ARGV[2] " +
            "if redis.call('EXISTS', key) == 0 then " +
            "  return '-1' " +
            "end " +
            "local baseFare = tonumber(redis.call('HGET', key, 'baseFare')) " +
            "local currentLowest = tonumber(redis.call('HGET', key, 'lowestBid')) " +
            "local lowestDriver = redis.call('HGET', key, 'lowestDriverId') " +
            "if currentLowest == nil then " +
            "  return '-1' " +
            "end " +
            "if newBid <= 0 or (baseFare ~= nil and newBid > (baseFare + 100)) then " +
            "  return '-2' " +
            "end " +
            "if lowestDriver == false or lowestDriver == 'NONE' then " +
            "  if newBid > currentLowest then " +
            "    return '0' " +
            "  end " +
            "else " +
            "  if newBid >= currentLowest then " +
            "    return '0' " +
            "  end " +
            "end " +
            "redis.call('HSET', key, 'lowestBid', tostring(newBid)) " +
            "redis.call('HSET', key, 'lowestDriverId', driverId) " +
            "return '1'";
        SUBMIT_BID_SCRIPT = new DefaultRedisScript<>(lua, String.class);
    }

    public AuctionRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void initAuction(Integer rideId, BigDecimal baseFare) {
        String key = KEY_PREFIX + rideId;

        Map<String, Object> auctionData = new HashMap<>();
        auctionData.put("rideId", String.valueOf(rideId));
        auctionData.put("lowestBid", baseFare.toString());
        auctionData.put("baseFare", baseFare.toString());
        auctionData.put("lowestDriverId", "NONE");
        auctionData.put("status", "ACTIVE");
        auctionData.put("durationSeconds", String.valueOf(ttlSeconds));

        redisTemplate.opsForHash().putAll(key, auctionData);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    public Map<Object, Object> getAuction(Integer rideId) {
        String key = KEY_PREFIX + rideId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries != null && !entries.isEmpty()) {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            entries.put("remainingSeconds", String.valueOf(expire != null && expire > 0 ? expire : 0L));
        }
        return entries;
    }

    public Long getRemainingSeconds(Integer rideId) {
        String key = KEY_PREFIX + rideId;
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire != null && expire > 0 ? expire : 0L;
    }

    public void closeAuction(Integer rideId) {
        redisTemplate.delete(KEY_PREFIX + rideId);
    }

    public boolean isAuctionActive(Integer rideId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + rideId));
    }

    public Map<String, Object> submitLiveBid(Integer rideId, Integer driverId, BigDecimal bidAmount) {
        String key = KEY_PREFIX + rideId;

        String result = redisTemplate.execute(
            SUBMIT_BID_SCRIPT,
            List.of(key),
            bidAmount.toString(),
            String.valueOf(driverId)
        );

        if ("-1".equals(result)) {
            throw new RuntimeException("Auction has expired or is not active.");
        }

        if ("-2".equals(result)) {
            Object baseFareObj = redisTemplate.opsForHash().get(key, "baseFare");
            String maxFare = baseFareObj != null ? new BigDecimal(baseFareObj.toString()).add(BigDecimal.valueOf(100)).toString() : "Base Fare + 100";
            throw new RuntimeException("Bid amount must be greater than 0 and cannot exceed max allowed fare of \u20b9" + maxFare + " (Base Fare + \u20b9100).");
        }

        if ("0".equals(result)) {
            Object currentLowestObj = redisTemplate.opsForHash().get(key, "lowestBid");
            Object lowestDriverObj = redisTemplate.opsForHash().get(key, "lowestDriverId");
            String currentLowest = currentLowestObj != null ? currentLowestObj.toString() : "unknown";
            boolean isFirstBid = lowestDriverObj == null || "NONE".equals(lowestDriverObj.toString());
            if (isFirstBid) {
                throw new RuntimeException(
                    "Bid rejected! Your initial bid (\u20b9" + bidAmount +
                    ") cannot exceed the base fare of \u20b9" + currentLowest + "."
                );
            }
            throw new RuntimeException(
                "Bid rejected! Your bid (\u20b9" + bidAmount +
                ") must be lower than the current lowest bid of \u20b9" + currentLowest + "."
            );
        }

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("rideId", rideId);
        resultMap.put("newLowestBid", bidAmount.toString());
        resultMap.put("lowestDriverId", String.valueOf(driverId));
        resultMap.put("status", "ACCEPTED_AS_LOWEST");
        return resultMap;
    }
}
