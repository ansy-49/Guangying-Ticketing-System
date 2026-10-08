package com.guangying.service.ratelimit;

import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.service.infrastructure.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 令牌桶策略：适合允许短时突发、限制长期平均速率的交易接口。
 */
@Component
@RequiredArgsConstructor
public class TokenBucketRateLimitStrategy implements RateLimitStrategy {

    private final RateLimiterService rateLimiterService;

    @Override
    public RateLimitAlgorithm algorithm() {
        return RateLimitAlgorithm.TOKEN_BUCKET;
    }

    @Override
    public boolean isAllowed(RateLimitContext context) {
        return rateLimiterService.isAllowedTokenBucket(
                context.resource(),
                context.identifier(),
                context.capacity(),
                context.refillRate()
        );
    }
}
