package com.guangying.service.mq.handler;

import com.guangying.common.constants.MQConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 订单创建事件：更新余票查询投影并失效座位图缓存。
 */
@Component
@RequiredArgsConstructor
public class OrderCreatedEventHandler implements OrderEventHandler {

    private final OrderEventProjectionSupport projectionSupport;

    @Override
    public String eventType() {
        return MQConstants.TAG_ORDER_CREATED;
    }

    @Override
    public void handle(Map<String, Object> event) {
        Long scheduleId = projectionSupport.toLong(event.get("scheduleId"));
        if (scheduleId == null) {
            return;
        }
        projectionSupport.syncSeatCount(scheduleId);
        projectionSupport.invalidateSeatLayout(scheduleId);
    }
}
