package com.guangying.service.mq.handler;

import com.guangying.common.constants.MQConstants;
import com.guangying.service.infrastructure.SeatSoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单支付事件：从数据库重建已售座位投影。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidEventHandler implements OrderEventHandler {

    private final SeatSoldService soldService;
    private final OrderEventProjectionSupport projectionSupport;

    @Override
    public String eventType() {
        return MQConstants.TAG_ORDER_PAID;
    }

    @Override
    public void handle(Map<String, Object> event) {
        Long scheduleId = projectionSupport.toLong(event.get("scheduleId"));
        if (scheduleId != null) {
            soldService.rebuildSold(scheduleId);
            projectionSupport.invalidateSeatLayout(scheduleId);
        }
        log.info("[OrderEvent] Paid event processed: orderNo={}", event.get("orderNo"));
    }
}
