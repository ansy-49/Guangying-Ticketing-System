package com.guangying.service.infrastructure;

import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 全局排队服务（Waiting Room） — 热门场次准入控制
 *
 * <h3>设计原则：</h3>
 * <pre>
 * 非热门场次直接放行，零开销
 * 热门场次由 Redis 控制并发入场人数上限，
 * 超限用户进入 ZSet 等候队列，前一人完成/超时后自动推进
 *
 * Redis Key 结构：
 *   queue:admission:{scheduleId}   — 当前入场人数（原子计数器）
 *   queue:max:{scheduleId}         — 最大同时入场人数
 *   queue:waiting:{scheduleId}     — 等候队列（ZSet, score=入场时间戳）
 *   queue:token:{scheduleId}:{userId} — 入场令牌（带 TTL）
 *
 * 入场令牌在以下时机释放：
 *   1. 锁座成功（用户已进入支付流程，名额释放给下一位）
 *   2. 订单取消/超时
 *   3. 令牌 TTL 自动过期
 * </pre>
 */
@Slf4j
@Service
public class QueueService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    // ==================== Lua 脚本：原子入场 ====================

    private static final String ENTER_LUA = """
            local admit_counter = KEYS[1]   -- queue:admission:{scheduleId}
            local max_counter   = KEYS[2]   -- queue:max:{scheduleId}
            local token_key     = KEYS[3]   -- queue:token:{scheduleId}:{userId}
            local waiting_zset  = KEYS[4]   -- queue:waiting:{scheduleId}

            local user_id       = ARGV[1]
            local token_ttl     = tonumber(ARGV[2])
            local now_ts        = tonumber(ARGV[3])

            -- 1. 已持有有效令牌 → 直接放行
            if redis.call('EXISTS', token_key) == 1 then
                return {1, 0, 0}
            end

            -- 2. 已在等候队列 → 返回当前位置
            local rank = redis.call('ZRANK', waiting_zset, user_id)
            if rank ~= false then
                return {0, rank + 1, rank * 30}
            end

            -- 3. 尝试入场
            local max = tonumber(redis.call('GET', max_counter) or '999999')
            local current = tonumber(redis.call('GET', admit_counter) or '0')

            if current < max then
                redis.call('SET', admit_counter, current + 1)
                redis.call('SET', token_key, '1', 'EX', token_ttl)
                return {1, 0, 0}
            else
                redis.call('ZADD', waiting_zset, now_ts, user_id)
                local new_rank = redis.call('ZRANK', waiting_zset, user_id)
                return {0, new_rank + 1, new_rank * 30}
            end
            """;

    // ==================== Lua 脚本：原子离场 + 推进 ====================

    private static final String LEAVE_AND_ADVANCE_LUA = """
            local admit_counter = KEYS[1]   -- queue:admission:{scheduleId}
            local waiting_zset  = KEYS[2]   -- queue:waiting:{scheduleId}
            local token_prefix  = KEYS[3]   -- queue:token:{scheduleId}:

            local token_ttl = tonumber(ARGV[1])

            -- 1. 释放当前名额
            local current = tonumber(redis.call('GET', admit_counter) or '0')
            if current > 0 then
                redis.call('DECR', admit_counter)
            end

            -- 2. 从等候队列推进下一位
            local next_user = redis.call('ZPOPMIN', waiting_zset)
            if next_user and #next_user > 0 then
                local next_user_id = next_user[1]
                redis.call('INCR', admit_counter)
                redis.call('SET', token_prefix .. next_user_id, '1', 'EX', token_ttl)
                return tonumber(next_user_id)
            end

            return 0
            """;

    private final DefaultRedisScript<List> enterScript = new DefaultRedisScript<>(ENTER_LUA, List.class);
    private final DefaultRedisScript<Long> leaveAndAdvanceScript = new DefaultRedisScript<>(LEAVE_AND_ADVANCE_LUA, Long.class);

    // ============================================================
    //  公共方法
    // ============================================================

    /**
     * 检查场次是否需要排队（热门标记）
     */
    public boolean isHotSchedule(Long scheduleId) {
        if (stringRedisTemplate == null) return false;
        try {
            String key = buildHotKey(scheduleId);
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("[Queue] Failed to check hot schedule: {}", scheduleId, e);
            return false;
        }
    }

    /**
     * 初始化热门场次准入上限
     *
     * @param maxAdmission 最大同时入场人数（建议 = 可售座位数 × 2~3）
     */
    public void initHotSchedule(Long scheduleId, int maxAdmission) {
        if (stringRedisTemplate == null) return;
        try {
            String maxKey = CacheConstants.QUEUE_MAX_PREFIX + scheduleId;
            stringRedisTemplate.opsForValue().set(maxKey, String.valueOf(maxAdmission));
            String hotKey = buildHotKey(scheduleId);
            stringRedisTemplate.opsForValue().set(hotKey, "1", 24, TimeUnit.HOURS); // 24h 自动过期
            log.info("[Queue] Hot schedule initialized: scheduleId={}, maxAdmission={}", scheduleId, maxAdmission);
        } catch (Exception e) {
            log.error("[Queue] Failed to init hot schedule: {}", scheduleId, e);
        }
    }

    /**
     * 尝试入场
     *
     * @return QueueEnterResult — admitted=true 表示获得入场令牌
     */
    public QueueEnterResult enter(Long scheduleId, Long userId) {
        // 非热门场次 → 直接放行
        if (!isHotSchedule(scheduleId)) {
            return QueueEnterResult.admitted();
        }

        if (stringRedisTemplate == null) {
            // Redis 不可用 → 开放入场（降级）
            return QueueEnterResult.admitted();
        }

        try {
            String admitKey = CacheConstants.QUEUE_ADMISSION_PREFIX + scheduleId;
            String maxKey = CacheConstants.QUEUE_MAX_PREFIX + scheduleId;
            String tokenKey = buildTokenKey(scheduleId, userId);
            String waitingKey = CacheConstants.QUEUE_WAITING_PREFIX + scheduleId;

            List<Object> result = stringRedisTemplate.execute(
                    enterScript,
                    Arrays.asList(admitKey, maxKey, tokenKey, waitingKey),
                    String.valueOf(userId),
                    String.valueOf(CacheConstants.QUEUE_TOKEN_TTL_SECONDS),
                    String.valueOf(System.currentTimeMillis())
            );

            if (result != null && result.size() >= 1) {
                boolean admitted = Number.class.cast(result.get(0)).intValue() == 1;
                int position = result.size() > 1 ? Number.class.cast(result.get(1)).intValue() : 0;
                int estimatedWait = result.size() > 2 ? Number.class.cast(result.get(2)).intValue() : 0;
                return new QueueEnterResult(admitted, position, estimatedWait);
            }
        } catch (Exception e) {
            log.error("[Queue] Enter failed: scheduleId={}, userId={}", scheduleId, userId, e);
            // Redis 异常 → 开放入场（降级安全）
        }

        return QueueEnterResult.admitted();
    }

    /**
     * 离场 — 用户完成锁座/订单取消/超时时调用
     *
     * <p>释放入场名额并自动推进等候队列的下一位入场</p>
     */
    public void leave(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId)) return;
        if (stringRedisTemplate == null) return;

        try {
            // 先删除当前用户的令牌
            String tokenKey = buildTokenKey(scheduleId, userId);
            stringRedisTemplate.delete(tokenKey);

            String admitKey = CacheConstants.QUEUE_ADMISSION_PREFIX + scheduleId;
            String waitingKey = CacheConstants.QUEUE_WAITING_PREFIX + scheduleId;
            String tokenPrefix = CacheConstants.QUEUE_TOKEN_PREFIX + scheduleId + ":";

            Long nextUserId = stringRedisTemplate.execute(
                    leaveAndAdvanceScript,
                    Arrays.asList(admitKey, waitingKey, tokenPrefix),
                    String.valueOf(CacheConstants.QUEUE_TOKEN_TTL_SECONDS)
            );

            if (nextUserId != null && nextUserId > 0) {
                log.info("[Queue] User {} left schedule {}, advanced user {}", userId, scheduleId, nextUserId);
            } else {
                log.debug("[Queue] User {} left schedule {}, no one waiting", userId, scheduleId);
            }
        } catch (Exception e) {
            log.error("[Queue] Leave failed: scheduleId={}, userId={}", scheduleId, userId, e);
        }
    }

    /**
     * 查询当前排队位置
     */
    public QueueStatusResult status(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId)) {
            return QueueStatusResult.admitted();
        }
        if (stringRedisTemplate == null) {
            return QueueStatusResult.admitted();
        }

        try {
            String tokenKey = buildTokenKey(scheduleId, userId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey))) {
                return QueueStatusResult.admitted();
            }

            String waitingKey = CacheConstants.QUEUE_WAITING_PREFIX + scheduleId;
            Long rank = stringRedisTemplate.opsForZSet().rank(waitingKey, String.valueOf(userId));
            if (rank != null) {
                int position = rank.intValue() + 1;
                int estimatedWait = rank.intValue() * CacheConstants.QUEUE_ESTIMATED_WAIT_PER_PERSON;
                return new QueueStatusResult(false, position, estimatedWait);
            }
        } catch (Exception e) {
            log.warn("[Queue] Status check failed: scheduleId={}", scheduleId, e);
        }

        return new QueueStatusResult(false, 999, 9999);
    }

    /**
     * 验证入场令牌是否有效
     */
    public boolean validateToken(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId)) return true;
        if (stringRedisTemplate == null) return true;

        try {
            String tokenKey = buildTokenKey(scheduleId, userId);
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey));
        } catch (Exception e) {
            log.warn("[Queue] Token validation failed: scheduleId={}, userId={}", scheduleId, userId, e);
            return true; // 降级放行
        }
    }

    // ==================== Key 构建 ====================

    private String buildHotKey(Long scheduleId) {
        return CacheConstants.SCHEDULE_HOT_KEY + ":" + scheduleId;
    }

    private String buildTokenKey(Long scheduleId, Long userId) {
        return CacheConstants.QUEUE_TOKEN_PREFIX + scheduleId + ":" + userId;
    }

    // ==================== 内部 VO ====================

    /**
     * 入场结果
     */
    public static class QueueEnterResult {
        private final boolean admitted;
        private final int position;
        private final int estimatedWaitSeconds;

        public QueueEnterResult(boolean admitted, int position, int estimatedWaitSeconds) {
            this.admitted = admitted;
            this.position = position;
            this.estimatedWaitSeconds = estimatedWaitSeconds;
        }

        public static QueueEnterResult admitted() {
            return new QueueEnterResult(true, 0, 0);
        }

        // Getters
        public boolean isAdmitted() { return admitted; }
        public int getPosition() { return position; }
        public int getEstimatedWaitSeconds() { return estimatedWaitSeconds; }
    }

    /**
     * 排队状态
     */
    public static class QueueStatusResult {
        private final boolean admitted;
        private final int position;
        private final int estimatedWaitSeconds;

        public QueueStatusResult(boolean admitted, int position, int estimatedWaitSeconds) {
            this.admitted = admitted;
            this.position = position;
            this.estimatedWaitSeconds = estimatedWaitSeconds;
        }

        public static QueueStatusResult admitted() {
            return new QueueStatusResult(true, 0, 0);
        }

        // Getters
        public boolean isAdmitted() { return admitted; }
        public int getPosition() { return position; }
        public int getEstimatedWaitSeconds() { return estimatedWaitSeconds; }
    }
}
