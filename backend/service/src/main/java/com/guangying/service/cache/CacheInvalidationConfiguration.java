package com.guangying.service.cache;

import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/** Redis Pub/Sub 广播写后失效，使每个应用实例都能及时清理自己的 Caffeine L1。 */
@Slf4j
@Configuration
@Profile("docker")
public class CacheInvalidationConfiguration {

    @Bean
    RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            MultiLevelCacheService cacheService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener = (Message message, byte[] pattern) -> {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                cacheService.onInvalidationMessage(payload);
            } catch (RuntimeException e) {
                log.warn("[Cache] Failed to apply invalidation message: {}", payload, e);
            }
        };
        container.addMessageListener(listener, new ChannelTopic(CacheConstants.CACHE_INVALIDATION_CHANNEL));
        return container;
    }
}
