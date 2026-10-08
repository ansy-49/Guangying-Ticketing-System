package com.guangying.service.ratelimit;

import com.guangying.common.enums.RateLimitAlgorithm;

/**
 * 限流算法策略。
 *
 * <p>每种算法独立实现，调用方只依赖统一接口。</p>
 */
public interface RateLimitStrategy {

    RateLimitAlgorithm algorithm();

    boolean isAllowed(RateLimitContext context);
}
