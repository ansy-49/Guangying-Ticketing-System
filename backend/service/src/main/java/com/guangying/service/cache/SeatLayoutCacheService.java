package com.guangying.service.cache;

import com.guangying.common.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 座位图渲染缓存的统一失效入口。
 *
 * <p>座位锁和已售状态分别以 Redis 锁、MySQL 为权威，渲染缓存只是
 * 可重建读模型。所有会改变座位可见状态的路径都通过该组件主动失效，
 * 短 TTL 和 MQ 投影重建仅作为最终兜底。</p>
 */
@Slf4j
@Component
public class SeatLayoutCacheService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** 幂等删除指定场次的公共座位图渲染缓存。 */
    public void invalidate(Long scheduleId) {
        if (stringRedisTemplate == null || scheduleId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CacheConstants.SEAT_LAYOUT_RENDERED_PREFIX + scheduleId);
        } catch (Exception e) {
            // 读模型失效不得反向影响已提交的交易，TTL 和 MQ 会继续兜底。
            log.warn("[SeatLayoutCache] Failed to invalidate rendered layout: scheduleId={}",
                    scheduleId, e);
        }
    }
}
