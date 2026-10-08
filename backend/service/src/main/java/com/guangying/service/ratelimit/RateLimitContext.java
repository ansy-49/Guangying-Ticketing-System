package com.guangying.service.ratelimit;

/**
 * 限流策略执行上下文。
 *
 * <p>将注解参数转换为稳定的领域输入，避免具体策略依赖 AOP 或 Web 层。</p>
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
