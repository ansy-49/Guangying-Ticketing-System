package com.guangying.service.mq.handler;

import com.guangying.common.constants.MQConstants;
import com.guangying.service.OrderCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 到达支付截止时间后检查订单；状态条件更新保证重复定时消息不会重复返库存。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCheckEventHandler implements OrderEventHandler {

    private final OrderCloseService orderCloseService;

    @Override
    public String eventType() {
        return MQConstants.TAG_ORDER_TIMEOUT_CHECK;
    }

    @Override
    public void handle(Map<String, Object> event) {
        String orderNo = (String) event.get("orderNo");
        OrderCloseService.CloseResult result = orderCloseService.closeExpired(
                orderNo, OrderCloseService.REASON_DELAY_MESSAGE);

        if (result == OrderCloseService.CloseResult.NOT_DUE) {
            // Broker/应用时钟有微小偏差时不提前关单，抛错让 RocketMQ 稍后重投。
            throw new IllegalStateException("Timeout event arrived before expire_time: " + orderNo);
        }
        if (result == OrderCloseService.CloseResult.NOT_FOUND) {
            throw new IllegalStateException("Timeout event references missing order: " + orderNo);
        }
        log.info("[OrderTimeout] Checked order: orderNo={}, result={}", orderNo, result);
    }
}
