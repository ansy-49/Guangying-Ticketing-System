package com.guangying.service.infrastructure;

import com.guangying.common.constants.CacheConstants;
import com.guangying.dao.mapper.OrderSeatMapper;
import com.guangying.domain.model.po.OrderSeatPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 已售座位投影服务 — Redis 作为 DB 的只读副本
 *
 * <p>铁律：已售态的权威在 DB（seat_lock.status=2 + order_seat），
 * Redis seat:sold Set 永远是 DB 的只读投影，任何时候都可以丢弃后从 DB 重建。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatSoldService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private final OrderSeatMapper orderSeatMapper;

    /**
     * 标记座位已售（支付成功后 best-effort）
     */
    public void markSold(Long scheduleId, List<String> seatMembers) {
        if (stringRedisTemplate == null || seatMembers == null || seatMembers.isEmpty()) {
            return;
        }
        try {
            String key = CacheConstants.scheduleKey(CacheConstants.SEAT_SOLD_SET_PREFIX, scheduleId);
            stringRedisTemplate.opsForSet().add(key, seatMembers.toArray(new String[0]));
            log.info("[SeatSold] Marked {} seats as sold: scheduleId={}", seatMembers.size(), scheduleId);
        } catch (Exception e) {
            log.error("[SeatSold] Failed to mark sold: scheduleId={}", scheduleId, e);
        }
    }

    /**
     * 获取已售座位集合
     */
    public Set<String> getSoldSeats(Long scheduleId) {
        if (stringRedisTemplate == null) {
            return loadFromDB(scheduleId);
        }
        try {
            String key = CacheConstants.scheduleKey(CacheConstants.SEAT_SOLD_SET_PREFIX, scheduleId);
            Set<String> sold = stringRedisTemplate.opsForSet().members(key);
            if (sold == null || sold.isEmpty()) {
                // 缓存为空，从 DB 重建
                return rebuildSold(scheduleId);
            }
            return sold;
        } catch (Exception e) {
            log.warn("[SeatSold] Redis read failed, fallback to DB: scheduleId={}", scheduleId, e);
            return loadFromDB(scheduleId);
        }
    }

    /**
     * 从 DB 重建已售投影（Warm-up / Redis 恢复后调用）
     */
    public Set<String> rebuildSold(Long scheduleId) {
        Set<String> sold = loadFromDB(scheduleId);
        if (stringRedisTemplate != null && !sold.isEmpty()) {
            try {
                String key = CacheConstants.scheduleKey(CacheConstants.SEAT_SOLD_SET_PREFIX, scheduleId);
                stringRedisTemplate.delete(key); // 先清再建
                stringRedisTemplate.opsForSet().add(key, sold.toArray(new String[0]));
                log.info("[SeatSold] Rebuilt sold projection: scheduleId={}, count={}", scheduleId, sold.size());
            } catch (Exception e) {
                log.error("[SeatSold] Failed to write sold to Redis: scheduleId={}", scheduleId, e);
            }
        }
        return sold;
    }

    /**
     * 从 DB 加载已售座位（降级 / 重建用）
     */
    private Set<String> loadFromDB(Long scheduleId) {
        List<OrderSeatPO> purchased = orderSeatMapper.selectPurchasedSeats(scheduleId);
        return purchased.stream()
                .map(os -> os.getRowNum() + "_" + os.getColNum())
                .collect(Collectors.toSet());
    }
}
