package com.guangying.service.mq.handler;

import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.SeatLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单取消事件：回滚余票查询投影并清理残留锁记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventHandler implements OrderEventHandler {

    private final SeatLockMapper seatLockMapper;
    private final OrderEventProjectionSupport projectionSupport;

    @Override
    public String eventType() {
        return MQConstants.TAG_ORDER_CANCELLED;
    }

    @Override
    public void handle(Map<String, Object> event) {
        Long scheduleId = projectionSupport.toLong(event.get("scheduleId"));
        if (scheduleId != null) {
            projectionSupport.syncSeatCount(scheduleId);
            projectionSupport.invalidateSeatLayout(scheduleId);
        }

        String orderNo = (String) event.get("orderNo");
        if (orderNo != null) {
            seatLockMapper.releaseOrderLocks(orderNo);
        }
        log.info("[OrderEvent] Cancelled event processed: orderNo={}", orderNo);
    }
}
