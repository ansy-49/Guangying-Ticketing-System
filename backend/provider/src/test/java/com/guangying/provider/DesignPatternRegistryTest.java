package com.guangying.provider;

import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.service.mq.handler.OrderEventHandler;
import com.guangying.service.mq.handler.OrderEventHandlerRegistry;
import com.guangying.service.ratelimit.RateLimitContext;
import com.guangying.service.ratelimit.RateLimitStrategy;
import com.guangying.service.ratelimit.RateLimitStrategyRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesignPatternRegistryTest {

    @Test
    void rateLimitRegistryRoutesByAlgorithm() {
        RateLimitStrategy slidingWindow = strategy(RateLimitAlgorithm.SLIDING_WINDOW);
        RateLimitStrategy tokenBucket = strategy(RateLimitAlgorithm.TOKEN_BUCKET);
        RateLimitStrategyRegistry registry =
                new RateLimitStrategyRegistry(List.of(slidingWindow, tokenBucket));

        assertSame(slidingWindow, registry.resolve(RateLimitAlgorithm.SLIDING_WINDOW));
        assertSame(tokenBucket, registry.resolve(RateLimitAlgorithm.TOKEN_BUCKET));
    }

    @Test
    void rateLimitRegistryRejectsDuplicateAlgorithms() {
        assertThrows(IllegalStateException.class, () ->
                new RateLimitStrategyRegistry(List.of(
                        strategy(RateLimitAlgorithm.SLIDING_WINDOW),
                        strategy(RateLimitAlgorithm.SLIDING_WINDOW)
                )));
    }

    @Test
    void orderEventRegistryRoutesCommandHandler() {
        AtomicBoolean handled = new AtomicBoolean();
        OrderEventHandler created = handler("ORDER_CREATED", handled);
        OrderEventHandlerRegistry registry =
                new OrderEventHandlerRegistry(List.of(created));

        registry.find("ORDER_CREATED").orElseThrow().handle(Map.of());

        assertTrue(handled.get());
        assertFalse(registry.find("UNKNOWN").isPresent());
    }

    @Test
    void orderEventRegistryRejectsDuplicateEventTypes() {
        assertThrows(IllegalStateException.class, () ->
                new OrderEventHandlerRegistry(List.of(
                        handler("ORDER_CREATED", new AtomicBoolean()),
                        handler("ORDER_CREATED", new AtomicBoolean())
                )));
    }

    private RateLimitStrategy strategy(RateLimitAlgorithm algorithm) {
        return new RateLimitStrategy() {
            @Override
            public RateLimitAlgorithm algorithm() {
                return algorithm;
            }

            @Override
            public boolean isAllowed(RateLimitContext context) {
                return true;
            }
        };
    }

    private OrderEventHandler handler(String eventType, AtomicBoolean handled) {
        return new OrderEventHandler() {
            @Override
            public String eventType() {
                return eventType;
            }

            @Override
            public void handle(Map<String, Object> event) {
                handled.set(true);
            }
        };
    }
}
