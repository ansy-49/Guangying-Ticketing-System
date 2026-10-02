package com.guangying.service.mq.handler;

import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.domain.model.po.SchedulePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单事件处理器共享的 Redis 投影操作。
 *
 * <p>这里只维护可重建的查询投影，不承载订单与已售座位的权威状态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProjectionSupport {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final ScheduleMapper scheduleMapper;

    /**
     * 从 MySQL 权威库存覆盖 Redis 展示计数。重复消费只会重复写入同一个值，
     * 不再通过 INCR/DECR 累积误差。
     */
    public void syncSeatCount(Long scheduleId) {
        if (stringRedisTemplate == null || scheduleId == null) {
            return;
        }
        try {
            SchedulePO schedule = scheduleMapper.selectById(scheduleId);
            if (schedule != null) {
                stringRedisTemplate.opsForValue().set(
                        CacheConstants.SEAT_COUNT_PREFIX + scheduleId,
                        String.valueOf(schedule.getAvailableSeats()));
            }
        } catch (Exception e) {
            log.warn("[OrderProjection] Failed to rebuild seat count: scheduleId={}",
                    scheduleId, e);
        }
    }

    /** @deprecated 使用 syncSeatCount 从权威数据重建，避免重复消息造成累计误差。 */
    @Deprecated
    public void decrementSeatCount(Long scheduleId, int count) {
        syncSeatCount(scheduleId);
    }

    /** @deprecated 使用 syncSeatCount 从权威数据重建，避免重复消息造成累计误差。 */
    @Deprecated
    public void incrementSeatCount(Long scheduleId, int count) {
        syncSeatCount(scheduleId);
    }

    public void invalidateSeatLayout(Long scheduleId) {
        if (stringRedisTemplate == null || scheduleId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CacheConstants.SEAT_LAYOUT_RENDERED_PREFIX + scheduleId);
        } catch (Exception e) {
            log.warn("[OrderProjection] Failed to invalidate seat layout: scheduleId={}",
                    scheduleId, e);
        }
    }

    public Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            return Long.parseLong(string);
        }
        return null;
    }

    public int toPositiveInt(Object value) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String string && !string.isBlank()) {
            return Math.max(0, Integer.parseInt(string));
        }
        return 0;
    }
}
