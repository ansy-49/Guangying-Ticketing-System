package com.guangying.service.ratelimit;

/**
 * 限流策略执行上下文，隔离注解/AOP 与具体算法。
 */
public record RateLimitContext(
        String resource,
        String identifier,
        int maxRequests,
        int windowSeconds,
        int capacity,
        int refillRate
) {
}
