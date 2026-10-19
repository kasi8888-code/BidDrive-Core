package com.biddrive.core.listeners;

import com.biddrive.core.services.AuctionFinishService;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Component
public class RedisExpirationListener extends KeyExpirationEventMessageListener {

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

                System.out.println("🔔 [Redis Expiry Event] 90-second auction timer ended for key: " + expiredKey);

                auctionFinishService.finalizeAuction(rideId);
            } catch (NumberFormatException e) {
                System.err.println("Failed to parse rideId from expired Redis key: " + expiredKey);
            }
        }
    }
}
