package com.guangying.service.ratelimit;

import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.service.infrastructure.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 滑动窗口策略：适合普通 API 的精确窗口计数。
 */
@Component
@RequiredArgsConstructor
public class SlidingWindowRateLimitStrategy implements RateLimitStrategy {

    private final RateLimiterService rateLimiterService;

    @Override
    public RateLimitAlgorithm algorithm() {
        return RateLimitAlgorithm.SLIDING_WINDOW;
    }

    @Override
    public boolean isAllowed(RateLimitContext context) {
        return rateLimiterService.isAllowed(
                context.resource(),
                context.identifier(),
                context.maxRequests(),
                context.windowSeconds()
        );
    }
}
