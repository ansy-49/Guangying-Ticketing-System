package com.guangying.service.infrastructure;

import com.guangying.common.constants.CacheConstants;
import com.guangying.domain.model.dto.LockSeatsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 座位锁 Lua 脚本服务 — 单座独立 Key，原子批量锁
 *
 * <h3>目标架构核心组件</h3>
 * <pre>
 *   Key:  seat:lock:{scheduleId}:{row}_{col}  — 单座独立 TTL
 *   TTL:  900 秒（15 分钟）
 *
 *   连座锁定: 一次 Lua 调用操作多个 Key
 *   同人幂等: owner == userId → 返回 ALREADY_OWNED，由业务层返回原订单
 * </pre>
 */
@Slf4j
@Service
public class SeatLockScriptService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** 座位锁默认 TTL（秒） */
    public static final int LOCK_TTL_SECONDS = CacheConstants.SEAT_LOCK_MINUTES * 60;

    /**
     * Lua 脚本：原子批量锁座（单座 Key + 同人幂等）
     *
     * <pre>
     * KEYS   = 各座位锁 key: seat:lock:{scheduleId}:{row}_{col} ...
     * ARGV[1] = userId
     * ARGV[2] = TTL 秒
     *
     * 返回: 1=本次新锁定成功; 2=至少一个座位已由本人持有; 0=被他人占用
     * </pre>
     */
    private static final String LOCK_SEATS_LUA = """
            local user_id = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local owned_by_user = false

            -- 第一阶段：预检。本人已持有时不刷新、不补锁，交给业务层返回原订单，
            -- 避免重试请求再次建单并在回滚补偿时释放第一次请求的锁。
            for i = 1, #KEYS do
                local owner = redis.call('GET', KEYS[i])
                if owner and owner ~= user_id then
                    return 0   -- 被他人锁定，原子拒绝
                end
                if owner == user_id then
                    owned_by_user = true
                end
            end

            if owned_by_user then
                return 2
            end

            -- 第二阶段：提交 —— 批量写锁，带独立 TTL
            for i = 1, #KEYS do
                redis.call('SET', KEYS[i], user_id, 'EX', ttl)
            end

            return 1
            """;

    /**
     * Lua 脚本：释放座位锁（仅释放本人持有的锁）
     *
     * <pre>
     * KEYS   = 各座位锁 key
     * ARGV[1] = userId
     *
     * 返回: 实际释放的座位数
     * </pre>
     */
    private static final String RELEASE_LOCKS_LUA = """
            local user_id = ARGV[1]
            local released = 0

            for i = 1, #KEYS do
                local owner = redis.call('GET', KEYS[i])
                if owner == user_id then
                    redis.call('DEL', KEYS[i])
                    released = released + 1
                end
            end

            return released
            """;

    /**
     * Lua 脚本：释放并标记已售（支付成功后调用）
     *
     * <pre>
     * KEYS   = 各座位锁 key
     * ARGV[1] = userId
     * KEYS[seatCount + 1] = soldSetKey (seat:sold:{scheduleId})
     * ARGV[2] = seatCount
     * ARGV[3...] = seat members (row_col)
     *
     * 返回: 成功释放的座位数
     * </pre>
     */
    private static final String RELEASE_LOCKS_AND_MARK_SOLD_LUA = """
            local user_id = ARGV[1]
            local seat_count = tonumber(ARGV[2])
            local sold_key = KEYS[seat_count + 1]
            local released = 0

            for i = 1, seat_count do
                local owner = redis.call('GET', KEYS[i])
                if owner == user_id then
                    redis.call('DEL', KEYS[i])
                    released = released + 1
                end
            end

            -- 写入已售投影
            for i = 3, #ARGV do
                redis.call('SADD', sold_key, ARGV[i])
            end

            return released
            """;

    private final DefaultRedisScript<Long> lockScript = new DefaultRedisScript<>(LOCK_SEATS_LUA, Long.class);
    private final DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(RELEASE_LOCKS_LUA, Long.class);
    private final DefaultRedisScript<Long> releaseAndMarkSoldScript = new DefaultRedisScript<>(RELEASE_LOCKS_AND_MARK_SOLD_LUA, Long.class);

    /**
     * 原子锁定多个座位
     *
     * @return 本次锁座结果；Redis Bean 缺失时由 MySQL 兜底，运行期 Redis 故障则安全拒绝
     */
    public LockResult lockSeats(Long scheduleId, List<LockSeatsDTO.SeatPos> seats, Long userId) {
        if (stringRedisTemplate == null) {
            log.debug("[SeatLock] Redis unavailable, fallback to DB-only locking");
            return LockResult.ACQUIRED; // 本地/H2 模式没有 Redis Bean，由 DB 唯一索引兜底
        }

        List<String> keys = new ArrayList<>();
        for (LockSeatsDTO.SeatPos seat : seats) {
            keys.add(buildSeatLockKey(scheduleId, seat.getRow(), seat.getCol()));
        }

        try {
            Long result = stringRedisTemplate.execute(
                    lockScript, keys,
                    String.valueOf(userId),
                    String.valueOf(LOCK_TTL_SECONDS)
            );
            LockResult lockResult = result == null
                    ? LockResult.UNAVAILABLE
                    : LockResult.fromCode(result.intValue());

            if (lockResult == LockResult.ACQUIRED) {
                // 维护辅助 Set（渲染加速，非权威）
                String lockedSetKey = CacheConstants.scheduleKey(
                        CacheConstants.SEAT_LOCKED_SET_PREFIX, scheduleId);
                for (LockSeatsDTO.SeatPos seat : seats) {
                    String member = seat.getRow() + "_" + seat.getCol();
                    stringRedisTemplate.opsForSet().add(lockedSetKey, member);
                }
                log.info("[SeatLock] Lua locked {} seats: scheduleId={}, userId={}", seats.size(), scheduleId, userId);
            } else if (lockResult == LockResult.ALREADY_OWNED) {
                log.info("[SeatLock] Idempotent retry detected: scheduleId={}, userId={}",
                        scheduleId, userId);
            } else {
                log.info("[SeatLock] Lua lock rejected: result={}, scheduleId={}, userId={}",
                        lockResult, scheduleId, userId);
            }

            return lockResult;
        } catch (Exception e) {
            log.error("[SeatLock] Lua lock error, fail closed: scheduleId={}, userId={}",
                    scheduleId, userId, e);
            return LockResult.UNAVAILABLE;
        }
    }

    /**
     * 释放用户锁定的座位
     *
     * @return 实际释放的座位数
     */
    public int releaseSeats(Long scheduleId, List<LockSeatsDTO.SeatPos> seats, Long userId) {
        if (stringRedisTemplate == null) {
            return seats.size();
        }

        List<String> keys = new ArrayList<>();
        for (LockSeatsDTO.SeatPos seat : seats) {
            keys.add(buildSeatLockKey(scheduleId, seat.getRow(), seat.getCol()));
        }

        try {
            Long released = stringRedisTemplate.execute(releaseScript, keys, String.valueOf(userId));
            int count = released != null ? released.intValue() : 0;

            // 清理辅助 Set
            String lockedSetKey = CacheConstants.scheduleKey(
                    CacheConstants.SEAT_LOCKED_SET_PREFIX, scheduleId);
            for (LockSeatsDTO.SeatPos seat : seats) {
                String member = seat.getRow() + "_" + seat.getCol();
                stringRedisTemplate.opsForSet().remove(lockedSetKey, member);
            }

            log.info("[SeatLock] Released {} seats: scheduleId={}, userId={}", count, scheduleId, userId);
            return count;
        } catch (Exception e) {
            log.error("[SeatLock] Failed to release seats: scheduleId={}, userId={}", scheduleId, userId, e);
            return 0;
        }
    }

    /**
     * 支付成功后：释放锁 + 写入已售投影
     */
    public void releaseLocksAndMarkSold(Long scheduleId, List<LockSeatsDTO.SeatPos> seats, Long userId) {
        if (stringRedisTemplate == null) {
            return;
        }

        List<String> keys = new ArrayList<>();
        List<String> members = new ArrayList<>();
        for (LockSeatsDTO.SeatPos seat : seats) {
            keys.add(buildSeatLockKey(scheduleId, seat.getRow(), seat.getCol()));
            members.add(seat.getRow() + "_" + seat.getCol());
        }

        String soldKey = CacheConstants.scheduleKey(CacheConstants.SEAT_SOLD_SET_PREFIX, scheduleId);
        keys.add(soldKey);
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(userId));
        args.add(String.valueOf(seats.size()));
        args.addAll(members);

        try {
            stringRedisTemplate.execute(releaseAndMarkSoldScript, keys, args.toArray());
            // 清理 locked 辅助 Set
            String lockedSetKey = CacheConstants.scheduleKey(
                    CacheConstants.SEAT_LOCKED_SET_PREFIX, scheduleId);
            for (String member : members) {
                stringRedisTemplate.opsForSet().remove(lockedSetKey, member);
            }
            log.info("[SeatLock] Released locks and marked sold: scheduleId={}, seats={}", scheduleId, members);
        } catch (Exception e) {
            log.error("[SeatLock] Failed to release locks and mark sold: scheduleId={}", scheduleId, e);
        }
    }

    /**
     * 检查某座位锁的状态
     *
     * @return null=空闲, userId=持有者
     */
    public String getSeatOwner(Long scheduleId, int row, int col) {
        if (stringRedisTemplate == null) {
            return null;
        }
        return stringRedisTemplate.opsForValue().get(buildSeatLockKey(scheduleId, row, col));
    }

    // =================== Key 构建 ===================

    public static String buildSeatLockKey(Long scheduleId, int row, int col) {
        return CacheConstants.scheduleKey(CacheConstants.SEAT_LOCK_KEY_PREFIX, scheduleId)
                + ":" + row + "_" + col;
    }

    public static String buildLockTokenKey(String lockToken) {
        return CacheConstants.SEAT_LOCK_TOKEN_PREFIX + lockToken;
    }

    public enum LockResult {
        CONFLICT(0),
        ACQUIRED(1),
        ALREADY_OWNED(2),
        UNAVAILABLE(-1);

        private final int code;

        LockResult(int code) {
            this.code = code;
        }

        private static LockResult fromCode(int code) {
            for (LockResult result : values()) {
                if (result.code == code) {
                    return result;
                }
            }
            return UNAVAILABLE;
        }
    }
}
