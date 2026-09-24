package com.guangying.service.infrastructure;

import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;

/**
 * Redis 分布式限流服务 — 滑动窗口 + 令牌桶双算法（面试重点）
 *
 * <h3>算法一：滑动窗口（ZSET）</h3>
 * <pre>
 * 使用 Redis ZSET，时间戳做 score + member：
 * 1. ZREMRANGEBYSCORE 移除窗口外旧记录
 * 2. ZCARD 统计窗口内请求数
 * 3. 未超限则 ZADD + PEXPIRE
 * 全程 Lua 脚本保证原子性。
 * 适用场景: 通用 API 限流，精确统计窗口内请求量。
 * </pre>
 *
 * <h3>算法二：令牌桶（String + Lua）</h3>
 * <pre>
 * Key 结构:
 *   {prefix}:tokens   → 当前令牌数（浮点数）
 *   {prefix}:ts       → 上次补充令牌的时间戳
 *
 * 每次请求:
 * 1. 根据时间差计算应补充的令牌数: elapsed * refillRate
 * 2. tokens = min(capacity, tokens + refilled)
 * 3. tokens >= 1 → 消耗一个令牌，允许通过
 * 4. tokens < 1  → 拒绝
 * 全程 Lua 脚本保证原子性。
 * 适用场景: 抢座/秒杀等突发流量，允许突发消耗 + 平滑限速。
 * </pre>
 *
 * <h3>算法对比（面试常问）：</h3>
 * <pre>
 * ┌─────────────┬────────────────────────────────────┐
 * │ 滑动窗口     │ 精确计数，窗口边界无突刺              │
 * │ 令牌桶       │ 允许突发(burst)，平均速率平滑         │
 * │ 漏桶(未实现)  │ 严格匀速，不允许突发                 │
 * └─────────────┴────────────────────────────────────┘
 * </pre>
 */
@Slf4j
@Service
public class RateLimiterService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    // =================== 算法一：滑动窗口 ===================

    /**
     * Lua 脚本：滑动窗口限流（Redis ZSET 原子操作）
     */
    private static final String SLIDING_WINDOW_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])

            -- 移除窗口外的过期记录
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)

            -- 当前窗口内的请求数
            local count = redis.call('ZCARD', key)

            if count < limit then
                -- 未超限，添加当前请求
                redis.call('ZADD', key, now, now .. '-' .. math.random(1000000))
                redis.call('PEXPIRE', key, window * 1000)
                return 1
            else
                return 0
            end
            """;

    // =================== 算法二：令牌桶 ===================

    /**
     * Lua 脚本：令牌桶限流（Redis String 原子操作）
     *
     * <pre>
     * KEYS[1] = 令牌数 key（{prefix}:tokens）
     * KEYS[2] = 时间戳 key（{prefix}:ts）
     * ARGV[1] = 当前时间戳（毫秒）
     * ARGV[2] = 桶容量（最大令牌数）
     * ARGV[3] = 每秒补充速率
     * ARGV[4] = TTL（秒），用于自动清理
     *
     * 返回: 1=允许, 0=限流
     * </pre>
     */
    private static final String TOKEN_BUCKET_LUA = """
            local tokens_key = KEYS[1]
            local ts_key = KEYS[2]
            local now = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_rate = tonumber(ARGV[3])
            local ttl = tonumber(ARGV[4])

            -- 读取当前令牌数和上次补充时间
            local last_tokens = tonumber(redis.call('GET', tokens_key))
            local last_ts = tonumber(redis.call('GET', ts_key))

            -- 首次访问：初始化为满桶
            if last_tokens == nil then
                last_tokens = capacity
                last_ts = now
            end

            -- 计算应补充的令牌数（基于时间差）
            local elapsed = math.max(0, now - last_ts) / 1000.0
            local refilled = elapsed * refill_rate
            local current_tokens = math.min(capacity, last_tokens + refilled)

            local allowed = 0
            if current_tokens >= 1 then
                -- 消耗一个令牌
                current_tokens = current_tokens - 1
                allowed = 1
            end

            -- 更新 Redis 状态
            redis.call('SET', tokens_key, tostring(current_tokens))
            redis.call('SET', ts_key, tostring(now))
            redis.call('EXPIRE', tokens_key, ttl)
            redis.call('EXPIRE', ts_key, ttl)

            return allowed
            """;

    private final DefaultRedisScript<Long> slidingWindowScript = new DefaultRedisScript<>(SLIDING_WINDOW_LUA, Long.class);
    private final DefaultRedisScript<Long> tokenBucketScript = new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);

    /**
     * 滑动窗口限流判断
     *
     * @param resource      资源标识
     * @param identifier    请求者标识（IP / userId）
     * @param maxRequests   窗口内最大请求数
     * @param windowSeconds 时间窗口(秒)
     * @return true=允许, false=限流
     */
    public boolean isAllowed(String resource, String identifier, int maxRequests, int windowSeconds) {
        if (stringRedisTemplate == null) {
            log.debug("[RateLimit] Redis unavailable, allowing by default");
            return true;
        }
        String key = CacheConstants.RATE_LIMIT_PREFIX + resource + ":" + identifier;
        try {
            Long result = stringRedisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(key),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests)
            );
            boolean allowed = result != null && result == 1L;
            if (!allowed) {
                log.warn("[RateLimit-SW] Blocked: resource={}, identifier={}", resource, identifier);
            }
            return allowed;
        } catch (Exception e) {
            log.error("[RateLimit-SW] Error, allowing by default", e);
            return true; // Redis 故障时放行（降级策略）
        }
    }

    /**
     * 令牌桶限流判断（面试亮点：适合突发流量场景）
     *
     * <p>工作原理：按 refillRate 速率持续补充令牌，桶满为 capacity。
     * 每次请求消耗一个令牌，桶空则拒绝。允许短时间突发（burst up to capacity）。</p>
     *
     * @param resource    资源标识
     * @param identifier  请求者标识（IP / userId）
     * @param capacity    桶容量（最大突发量）
     * @param refillRate  每秒补充令牌数（平均速率）
     * @return true=允许, false=限流
     */
    public boolean isAllowedTokenBucket(String resource, String identifier, int capacity, int refillRate) {
        if (stringRedisTemplate == null) {
            log.debug("[RateLimit-TB] Redis unavailable, allowing by default");
            return true;
        }
        String prefix = CacheConstants.RATE_LIMIT_PREFIX + "tb:" + resource + ":" + identifier;
        String tokensKey = prefix + ":tokens";
        String tsKey = prefix + ":ts";
        // TTL = 桶从空到满所需时间的2倍，确保 key 不过早过期
        int ttl = Math.max(60, (capacity / Math.max(1, refillRate)) * 2);

        try {
            Long result = stringRedisTemplate.execute(
                    tokenBucketScript,
                    Arrays.asList(tokensKey, tsKey),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    String.valueOf(ttl)
            );
            boolean allowed = result != null && result == 1L;
            if (!allowed) {
                log.warn("[RateLimit-TB] Blocked: resource={}, identifier={}, capacity={}, rate={}/s",
                        resource, identifier, capacity, refillRate);
            }
            return allowed;
        } catch (Exception e) {
            log.error("[RateLimit-TB] Error, allowing by default", e);
            return true; // Redis 故障时放行（降级策略）
        }
    }
}
