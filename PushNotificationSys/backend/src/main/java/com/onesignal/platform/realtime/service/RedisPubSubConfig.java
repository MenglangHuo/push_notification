package com.onesignal.platform.realtime.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public MessageListenerAdapter realtimeNotificationListenerAdapter(RealtimeNotificationRelayService relayService) {
        return new MessageListenerAdapter(relayService, "onRedisMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter realtimeNotificationListenerAdapter
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                realtimeNotificationListenerAdapter,
                new ChannelTopic(RealtimeNotificationRelayService.REDIS_CHANNEL)
        );
        return container;
    }
}
