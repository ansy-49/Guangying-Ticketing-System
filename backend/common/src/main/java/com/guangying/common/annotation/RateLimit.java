package com.guangying.common.annotation;

import com.guangying.common.enums.RateLimitAlgorithm;

import java.lang.annotation.*;

/**
 * 限流注解 — 支持滑动窗口 & 令牌桶双算法（面试亮点）
 *
 * <h3>使用示例:</h3>
 * <pre>
 * // 1. 通用 API 限流（默认滑动窗口）
 * {@code @RateLimit(key = "wish", maxRequests = 10, windowSeconds = 60)}
 *
 * // 2. 抢座/秒杀场景（令牌桶，允许突发）
 * {@code @RateLimit(key = "seat:lock", algorithm = TOKEN_BUCKET, capacity = 20, refillRate = 5)}
 * </pre>
 *
 * <h3>算法选择建议：</h3>
 * <pre>
 * 滑动窗口: 精确控制窗口内请求量，适合普通接口
 * 令牌桶:   允许突发 + 平滑限速，适合抢座/下单等突发场景
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流资源标识 */
    String key() default "";

    /** 限流算法（默认滑动窗口） */
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.SLIDING_WINDOW;

    // =================== 滑动窗口参数 ===================

    /** 窗口内最大请求数（滑动窗口算法使用） */
    int maxRequests() default 100;

    /** 时间窗口(秒)（滑动窗口算法使用） */
    int windowSeconds() default 60;

    // =================== 令牌桶参数 ===================

    /** 桶容量（令牌桶算法使用，决定最大突发量） */
    int capacity() default 20;

    /** 每秒补充令牌数（令牌桶算法使用，决定平均限速） */
    int refillRate() default 5;
}
