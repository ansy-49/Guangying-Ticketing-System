package com.guangying.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.CacheConstants;
import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.dao.mapper.SeatLockMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.model.po.OrderPO;
import com.guangying.service.infrastructure.SeatSoldService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单事件消费者 — CONCURRENTLY + 幂等
 *
 * <p>消费 ORDER_TOPIC 的所有 Tag。
 * MQ 不参与建单关键路径，只做下游解耦（缓存失效、计数更新、出票/短信等）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = MQConstants.ORDER_TOPIC,
        consumerGroup = MQConstants.ORDER_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        selectorExpression = "*"
)
public class OrderEventConsumer implements RocketMQListener<String> {

    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final SeatLockMapper seatLockMapper;
    private final SeatSoldService soldService;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    public OrderEventConsumer(OrderMapper orderMapper,
                              ScheduleMapper scheduleMapper,
                              SeatLockMapper seatLockMapper,
                              SeatSoldService soldService,
                              ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.scheduleMapper = scheduleMapper;
        this.seatLockMapper = seatLockMapper;
        this.soldService = soldService;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String type = (String) event.get("type");
            String orderNo = (String) event.get("orderNo");

            if (orderNo == null) {
                log.warn("[OrderConsumer] Missing orderNo, skip");
                return;
            }

            log.info("[OrderConsumer] Received: type={}, orderNo={}", type, orderNo);

            switch (type) {
                case "ORDER_CREATED" -> handleOrderCreated(event);
                case "ORDER_PAID" -> handleOrderPaid(event);
                case "ORDER_CANCELLED" -> handleOrderCancelled(event);
                case "ORDER_TIMEOUT" -> handleOrderTimeout(event);
                default -> log.debug("[OrderConsumer] Unknown type: {}", type);
            }
        } catch (Exception e) {
            log.error("[OrderConsumer] Failed to process message", e);
            throw new RuntimeException("Order event processing failed, trigger retry", e);
        }
    }

    /**
     * 订单创建：更新余票计数 + 失效渲染缓存
     */
    private void handleOrderCreated(Map<String, Object> event) {
        Long scheduleId = toLong(event.get("scheduleId"));
        if (scheduleId != null) {
            decrementSeatCount(scheduleId);
            invalidateRenderCache(scheduleId);
        }
    }

    /**
     * 订单支付：更新已售投影 + 失效渲染缓存 + 下游（出票/短信/积分）逻辑
     */
    private void handleOrderPaid(Map<String, Object> event) {
        Long scheduleId = toLong(event.get("scheduleId"));
        String orderNo = (String) event.get("orderNo");

        if (scheduleId != null) {
            // 已售投影从 DB 重建（保证准确）
            soldService.rebuildSold(scheduleId);
            invalidateRenderCache(scheduleId);
        }

        log.info("[OrderConsumer] Order paid processed: orderNo={}", orderNo);
    }

    /**
     * 订单取消：释放锁 + 回滚计数 + 失效缓存
     */
    private void handleOrderCancelled(Map<String, Object> event) {
        Long scheduleId = toLong(event.get("scheduleId"));
        String orderNo = (String) event.get("orderNo");
        Object seatCountObj = event.get("seatCount");

        if (scheduleId != null) {
            // 更新渲染缓存
            invalidateRenderCache(scheduleId);

            // 回滚余票计数
            int count = seatCountObj instanceof Number ? ((Number) seatCountObj).intValue() : 0;
            if (count > 0) {
                incrementSeatCount(scheduleId, count);
            }
        }

        // 释放锁记录
        if (orderNo != null) {
            seatLockMapper.releaseOrderLocks(orderNo);
        }

        log.info("[OrderConsumer] Order cancelled processed: orderNo={}", orderNo);
    }

    /**
     * 超时关单：检查订单状态 → 关单 + 释放锁
     */
    private void handleOrderTimeout(Map<String, Object> event) {
        String orderNo = (String) event.get("orderNo");
        if (orderNo == null) return;

        try {
            OrderPO order = orderMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderPO>()
                            .eq(OrderPO::getOrderNo, orderNo));
            if (order == null) return;

            // 已支付或已关闭 → 丢弃
            if (order.getStatus() == OrderStatusEnum.PAID.getCode()
                    || order.getStatus() == OrderStatusEnum.CANCELLED.getCode()) {
                return;
            }

            // WAIT_PAY → 关单
            if (order.getStatus() == OrderStatusEnum.PENDING.getCode()) {
                LocalDateTime now = LocalDateTime.now();
                int closed = orderMapper.closePendingOrder(orderNo, now);
                if (closed > 0) {
                    scheduleMapper.rollbackStock(order.getScheduleId(), order.getSeatCount());
                    seatLockMapper.releaseOrderLocks(orderNo);
                    invalidateRenderCache(order.getScheduleId());
                    log.info("[OrderConsumer] Timeout closed: orderNo={}", orderNo);
                }
            }
        } catch (Exception e) {
            log.error("[OrderConsumer] Timeout check failed: orderNo={}", orderNo, e);
            throw new RuntimeException(e);
        }
    }

    // =================== 辅助方法 ===================

    private void decrementSeatCount(Long scheduleId) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.opsForValue().decrement(CacheConstants.SEAT_COUNT_PREFIX + scheduleId);
        } catch (Exception e) {
            log.warn("[OrderConsumer] Failed to decrement seat count: scheduleId={}", scheduleId, e);
        }
    }

    private void incrementSeatCount(Long scheduleId, int count) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.opsForValue().increment(CacheConstants.SEAT_COUNT_PREFIX + scheduleId, count);
        } catch (Exception e) {
            log.warn("[OrderConsumer] Failed to increment seat count: scheduleId={}", scheduleId, e);
        }
    }

    private void invalidateRenderCache(Long scheduleId) {
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.delete(CacheConstants.SEAT_LAYOUT_RENDERED_PREFIX + scheduleId);
        } catch (Exception e) {
            log.warn("[OrderConsumer] Failed to invalidate render cache: scheduleId={}", scheduleId, e);
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
