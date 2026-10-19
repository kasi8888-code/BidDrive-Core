package com.biddrive.core.listeners;

import com.biddrive.core.services.AuctionFinishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class RedisExpirationListener extends KeyExpirationEventMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisExpirationListener.class);

    private final AuctionFinishService auctionFinishService;

    public RedisExpirationListener(RedisMessageListenerContainer listenerContainer,
                                  AuctionFinishService auctionFinishService) {
        super(listenerContainer);
        this.auctionFinishService = auctionFinishService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        if (expiredKey != null && expiredKey.startsWith("auction:ride:")) {
            try {
                String rideIdStr = expiredKey.substring("auction:ride:".length());
                Integer rideId = Integer.parseInt(rideIdStr);

                log.info("🔔 [Redis Expiry Event] Auction timer ended for key: {}", expiredKey);

                auctionFinishService.finalizeAuction(rideId);
            } catch (NumberFormatException e) {
                log.warn("Failed to parse rideId from expired Redis key: {}", expiredKey);
            }
        }
    }
}
