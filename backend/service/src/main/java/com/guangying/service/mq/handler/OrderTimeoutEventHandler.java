package com.guangying.service.mq.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.dao.mapper.SeatLockMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.model.po.OrderPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 订单超时命令：使用订单状态机 CAS 关闭待支付订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutEventHandler implements OrderEventHandler {

    public static final String EVENT_TYPE = "ORDER_TIMEOUT";

    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final SeatLockMapper seatLockMapper;
    private final OrderEventProjectionSupport projectionSupport;

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(Map<String, Object> event) {
        String orderNo = (String) event.get("orderNo");
        if (orderNo == null) {
            return;
        }

        OrderPO order = orderMapper.selectOne(
                new LambdaQueryWrapper<OrderPO>().eq(OrderPO::getOrderNo, orderNo));
        if (order == null
                || order.getStatus() == OrderStatusEnum.PAID.getCode()
                || order.getStatus() == OrderStatusEnum.CANCELLED.getCode()) {
            return;
        }

        if (order.getStatus() == OrderStatusEnum.PENDING.getCode()) {
            int closed = orderMapper.closePendingOrder(orderNo, LocalDateTime.now());
            if (closed > 0) {
                scheduleMapper.rollbackStock(order.getScheduleId(), order.getSeatCount());
                seatLockMapper.releaseOrderLocks(orderNo);
                projectionSupport.syncSeatCount(order.getScheduleId());
                projectionSupport.invalidateSeatLayout(order.getScheduleId());
                log.info("[OrderEvent] Timeout order closed: orderNo={}", orderNo);
            }
        }
    }
}
