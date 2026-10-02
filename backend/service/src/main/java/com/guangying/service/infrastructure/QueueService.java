package com.guangying.service.infrastructure;

import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 热门场次 Waiting Room。
 *
 * <p>每个已入场用户同时拥有 token key 和 lease ZSet 记录。token 用于快速鉴权，
 * lease 的 score 保存到期时间，定时任务负责回收过期租约并推进等待用户。</p>
 */
@Slf4j
@Service
public class QueueService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String ENTER_LUA = """
            local admit_key = KEYS[1]
            local max_key = KEYS[2]
            local token_key = KEYS[3]
            local waiting_key = KEYS[4]
            local lease_key = KEYS[5]
            local user_id = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local token_prefix = ARGV[4]
            local expires_at = now + ttl * 1000

            local expired = redis.call('ZRANGEBYSCORE', lease_key, '-inf', now)
            for _, expired_user in ipairs(expired) do
                redis.call('ZREM', lease_key, expired_user)
                redis.call('DEL', token_prefix .. expired_user)
            end

            local max = tonumber(redis.call('GET', max_key) or '999999')
            while redis.call('ZCARD', lease_key) < max do
                local next_user = redis.call('ZPOPMIN', waiting_key)
                if not next_user or #next_user == 0 then break end
                local next_id = next_user[1]
                redis.call('ZADD', lease_key, expires_at, next_id)
                redis.call('SET', token_prefix .. next_id, '1', 'EX', ttl)
            end

            if redis.call('EXISTS', token_key) == 1 then
                local remaining = redis.call('PTTL', token_key)
                if remaining > 0 then
                    redis.call('ZADD', lease_key, now + remaining, user_id)
                end
                redis.call('SET', admit_key, redis.call('ZCARD', lease_key))
                return {1, 0, 0}
            end

            local rank = redis.call('ZRANK', waiting_key, user_id)
            if rank ~= false then
                redis.call('SET', admit_key, redis.call('ZCARD', lease_key))
                return {0, rank + 1, rank * 30}
            end

            if redis.call('ZCARD', lease_key) < max then
                redis.call('ZADD', lease_key, expires_at, user_id)
                redis.call('SET', token_key, '1', 'EX', ttl)
                redis.call('SET', admit_key, redis.call('ZCARD', lease_key))
                return {1, 0, 0}
            end

            redis.call('ZADD', waiting_key, now, user_id)
            rank = redis.call('ZRANK', waiting_key, user_id)
            redis.call('SET', admit_key, redis.call('ZCARD', lease_key))
            return {0, rank + 1, rank * 30}
            """;

    private static final String LEAVE_LUA = """
            local admit_key = KEYS[1]
            local max_key = KEYS[2]
            local waiting_key = KEYS[3]
            local lease_key = KEYS[4]
            local token_key = KEYS[5]
            local user_id = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local token_prefix = ARGV[4]
            local expires_at = now + ttl * 1000

            redis.call('DEL', token_key)
            redis.call('ZREM', waiting_key, user_id)
            local released = redis.call('ZREM', lease_key, user_id)
            local max = tonumber(redis.call('GET', max_key) or '999999')
            local advanced = 0

            if released == 1 then
                while redis.call('ZCARD', lease_key) < max do
                    local next_user = redis.call('ZPOPMIN', waiting_key)
                    if not next_user or #next_user == 0 then break end
                    local next_id = next_user[1]
                    redis.call('ZADD', lease_key, expires_at, next_id)
                    redis.call('SET', token_prefix .. next_id, '1', 'EX', ttl)
                    if advanced == 0 then advanced = tonumber(next_id) end
                end
            end

            redis.call('SET', admit_key, redis.call('ZCARD', lease_key))
            return {released, advanced}
            """;

    private static final String REAP_LUA = """
            local admit_key = KEYS[1]
            local max_key = KEYS[2]
            local waiting_key = KEYS[3]
            local lease_key = KEYS[4]
            local ttl = tonumber(ARGV[1])
            local now = tonumber(ARGV[2])
            local token_prefix = ARGV[3]
            local expires_at = now + ttl * 1000

            local expired = redis.call('ZRANGEBYSCORE', lease_key, '-inf', now)
            for _, expired_user in ipairs(expired) do
                redis.call('ZREM', lease_key, expired_user)
                redis.call('DEL', token_prefix .. expired_user)
            end

            local max = tonumber(redis.call('GET', max_key) or '999999')
            local advanced = 0
            while redis.call('ZCARD', lease_key) < max do
                local next_user = redis.call('ZPOPMIN', waiting_key)
                if not next_user or #next_user == 0 then break end
                local next_id = next_user[1]
                redis.call('ZADD', lease_key, expires_at, next_id)
                redis.call('SET', token_prefix .. next_id, '1', 'EX', ttl)
                advanced = advanced + 1
            end

            local current = redis.call('ZCARD', lease_key)
            redis.call('SET', admit_key, current)
            return {#expired, advanced, current}
            """;

    private final DefaultRedisScript<List> enterScript =
            new DefaultRedisScript<>(ENTER_LUA, List.class);
    private final DefaultRedisScript<List> leaveScript =
            new DefaultRedisScript<>(LEAVE_LUA, List.class);
    private final DefaultRedisScript<List> reapScript =
            new DefaultRedisScript<>(REAP_LUA, List.class);

    public boolean isHotSchedule(Long scheduleId) {
        if (stringRedisTemplate == null) return false;
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildHotKey(scheduleId)));
        } catch (Exception e) {
            log.warn("[Queue] Failed to check hot schedule: {}", scheduleId, e);
            return false;
        }
    }

    public void initHotSchedule(Long scheduleId, int maxAdmission) {
        if (stringRedisTemplate == null) return;
        if (maxAdmission <= 0) throw new IllegalArgumentException("maxAdmission must be positive");
        try {
            stringRedisTemplate.opsForValue().set(
                    CacheConstants.QUEUE_MAX_PREFIX + scheduleId, String.valueOf(maxAdmission));
            stringRedisTemplate.opsForValue().set(buildHotKey(scheduleId), "1", 24, TimeUnit.HOURS);
            stringRedisTemplate.opsForSet().add(
                    CacheConstants.QUEUE_HOT_SCHEDULES_KEY, String.valueOf(scheduleId));
            log.info("[Queue] Hot schedule initialized: scheduleId={}, maxAdmission={}",
                    scheduleId, maxAdmission);
        } catch (Exception e) {
            log.error("[Queue] Failed to init hot schedule: {}", scheduleId, e);
        }
    }

    public QueueEnterResult enter(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId) || stringRedisTemplate == null) {
            return QueueEnterResult.allowed();
        }
        try {
            List<Object> result = stringRedisTemplate.execute(
                    enterScript, enterKeys(scheduleId, userId),
                    String.valueOf(userId),
                    String.valueOf(CacheConstants.QUEUE_TOKEN_TTL_SECONDS),
                    String.valueOf(System.currentTimeMillis()),
                    tokenPrefix(scheduleId));
            if (result != null && !result.isEmpty()) {
                boolean admitted = ((Number) result.get(0)).intValue() == 1;
                int position = result.size() > 1 ? ((Number) result.get(1)).intValue() : 0;
                int estimated = result.size() > 2 ? ((Number) result.get(2)).intValue() : 0;
                return new QueueEnterResult(admitted, position, estimated);
            }
        } catch (Exception e) {
            log.error("[Queue] Enter failed: scheduleId={}, userId={}", scheduleId, userId, e);
        }
        return QueueEnterResult.allowed();
    }

    /** 幂等离场：只有成功删除租约时才会释放容量并推进下一位。 */
    public void leave(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId) || stringRedisTemplate == null) return;
        try {
            List<Object> result = stringRedisTemplate.execute(
                    leaveScript,
                    Arrays.asList(
                            CacheConstants.QUEUE_ADMISSION_PREFIX + scheduleId,
                            CacheConstants.QUEUE_MAX_PREFIX + scheduleId,
                            CacheConstants.QUEUE_WAITING_PREFIX + scheduleId,
                            CacheConstants.QUEUE_LEASE_PREFIX + scheduleId,
                            buildTokenKey(scheduleId, userId)),
                    String.valueOf(userId),
                    String.valueOf(CacheConstants.QUEUE_TOKEN_TTL_SECONDS),
                    String.valueOf(System.currentTimeMillis()),
                    tokenPrefix(scheduleId));
            int released = result == null || result.isEmpty() ? 0 : ((Number) result.get(0)).intValue();
            long advanced = result == null || result.size() < 2 ? 0 : ((Number) result.get(1)).longValue();
            log.debug("[Queue] Leave schedule={}, user={}, released={}, advanced={}",
                    scheduleId, userId, released, advanced);
        } catch (Exception e) {
            log.error("[Queue] Leave failed: scheduleId={}, userId={}", scheduleId, userId, e);
        }
    }

    /** 每 5 秒回收过期租约，并按 FIFO 自动补位。 */
    @Scheduled(fixedDelayString = "${guangying.queue.reap-interval-ms:5000}")
    public void reapExpiredLeases() {
        if (stringRedisTemplate == null) return;
        try {
            Set<String> schedules = stringRedisTemplate.opsForSet()
                    .members(CacheConstants.QUEUE_HOT_SCHEDULES_KEY);
            if (schedules == null || schedules.isEmpty()) return;
            for (String value : schedules) {
                Long scheduleId;
                try {
                    scheduleId = Long.valueOf(value);
                } catch (NumberFormatException ignored) {
                    stringRedisTemplate.opsForSet().remove(
                            CacheConstants.QUEUE_HOT_SCHEDULES_KEY, value);
                    continue;
                }
                if (!isHotSchedule(scheduleId)) {
                    stringRedisTemplate.opsForSet().remove(
                            CacheConstants.QUEUE_HOT_SCHEDULES_KEY, value);
                    continue;
                }
                reapSchedule(scheduleId);
            }
        } catch (Exception e) {
            log.warn("[Queue] Lease reaper failed", e);
        }
    }

    private void reapSchedule(Long scheduleId) {
        List<Object> result = stringRedisTemplate.execute(
                reapScript,
                Arrays.asList(
                        CacheConstants.QUEUE_ADMISSION_PREFIX + scheduleId,
                        CacheConstants.QUEUE_MAX_PREFIX + scheduleId,
                        CacheConstants.QUEUE_WAITING_PREFIX + scheduleId,
                        CacheConstants.QUEUE_LEASE_PREFIX + scheduleId),
                String.valueOf(CacheConstants.QUEUE_TOKEN_TTL_SECONDS),
                String.valueOf(System.currentTimeMillis()),
                tokenPrefix(scheduleId));
        if (result != null && result.size() >= 2) {
            int expired = ((Number) result.get(0)).intValue();
            int advanced = ((Number) result.get(1)).intValue();
            if (expired > 0 || advanced > 0) {
                log.info("[Queue] Reaped schedule={}, expired={}, advanced={}",
                        scheduleId, expired, advanced);
            }
        }
    }

    public QueueStatusResult status(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId) || stringRedisTemplate == null) {
            return QueueStatusResult.allowed();
        }
        try {
            reapSchedule(scheduleId);
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(buildTokenKey(scheduleId, userId)))) {
                return QueueStatusResult.allowed();
            }
            Long rank = stringRedisTemplate.opsForZSet().rank(
                    CacheConstants.QUEUE_WAITING_PREFIX + scheduleId, String.valueOf(userId));
            if (rank != null) {
                int position = rank.intValue() + 1;
                return new QueueStatusResult(false, position,
                        rank.intValue() * CacheConstants.QUEUE_ESTIMATED_WAIT_PER_PERSON);
            }
        } catch (Exception e) {
            log.warn("[Queue] Status check failed: scheduleId={}", scheduleId, e);
        }
        return new QueueStatusResult(false, 999, 9999);
    }

    public boolean validateToken(Long scheduleId, Long userId) {
        if (!isHotSchedule(scheduleId) || stringRedisTemplate == null) return true;
        try {
            Boolean tokenExists = stringRedisTemplate.hasKey(buildTokenKey(scheduleId, userId));
            Double lease = stringRedisTemplate.opsForZSet().score(
                    CacheConstants.QUEUE_LEASE_PREFIX + scheduleId, String.valueOf(userId));
            return Boolean.TRUE.equals(tokenExists)
                    && lease != null
                    && lease > System.currentTimeMillis();
        } catch (Exception e) {
            log.warn("[Queue] Token validation failed: scheduleId={}, userId={}",
                    scheduleId, userId, e);
            return true;
        }
    }

    private List<String> enterKeys(Long scheduleId, Long userId) {
        return Arrays.asList(
                CacheConstants.QUEUE_ADMISSION_PREFIX + scheduleId,
                CacheConstants.QUEUE_MAX_PREFIX + scheduleId,
                buildTokenKey(scheduleId, userId),
                CacheConstants.QUEUE_WAITING_PREFIX + scheduleId,
                CacheConstants.QUEUE_LEASE_PREFIX + scheduleId);
    }

    private String buildHotKey(Long scheduleId) {
        return CacheConstants.SCHEDULE_HOT_KEY + ":" + scheduleId;
    }

    private String buildTokenKey(Long scheduleId, Long userId) {
        return tokenPrefix(scheduleId) + userId;
    }

    private String tokenPrefix(Long scheduleId) {
        return CacheConstants.QUEUE_TOKEN_PREFIX + scheduleId + ":";
    }

    public record QueueEnterResult(boolean admitted, int position, int estimatedWaitSeconds) {
        public static QueueEnterResult allowed() {
            return new QueueEnterResult(true, 0, 0);
        }
        public boolean isAdmitted() { return admitted; }
        public int getPosition() { return position; }
        public int getEstimatedWaitSeconds() { return estimatedWaitSeconds; }
    }

    public record QueueStatusResult(boolean admitted, int position, int estimatedWaitSeconds) {
        public static QueueStatusResult allowed() {
            return new QueueStatusResult(true, 0, 0);
        }
        public boolean isAdmitted() { return admitted; }
        public int getPosition() { return position; }
        public int getEstimatedWaitSeconds() { return estimatedWaitSeconds; }
    }
}
