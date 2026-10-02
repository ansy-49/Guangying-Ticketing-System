package com.guangying.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.ProcessedEventMapper;
import com.guangying.domain.model.po.ProcessedEventPO;
import com.guangying.service.mq.handler.OrderEventHandlerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    private final ObjectMapper objectMapper;
    private final OrderEventHandlerRegistry handlerRegistry;
    private final ProcessedEventMapper processedEventMapper;

    public OrderEventConsumer(ObjectMapper objectMapper,
                              OrderEventHandlerRegistry handlerRegistry,
                              ProcessedEventMapper processedEventMapper) {
        this.objectMapper = objectMapper;
        this.handlerRegistry = handlerRegistry;
        this.processedEventMapper = processedEventMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String type = (String) event.get("type");
            String orderNo = (String) event.get("orderNo");
            String eventId = (String) event.get("eventId");

            if (orderNo == null) {
                log.warn("[OrderConsumer] Missing orderNo, skip");
                return;
            }
            if (eventId == null || eventId.isBlank()) {
                // 兼容升级前产生的消息，同一订单同一事件类型仍保持稳定幂等键。
                eventId = type + ":" + orderNo;
            }
            if (processedEventMapper.selectById(eventId) != null) {
                log.debug("[OrderConsumer] Duplicate event skipped: eventId={}", eventId);
                return;
            }

            log.info("[OrderConsumer] Received: eventId={}, type={}, orderNo={}",
                    eventId, type, orderNo);

            handlerRegistry.find(type)
                    .ifPresentOrElse(
                            handler -> handler.handle(event),
                            () -> log.debug("[OrderConsumer] Unknown type: {}", type)
                    );

            ProcessedEventPO processed = new ProcessedEventPO();
            processed.setEventId(eventId);
            processed.setEventType(type == null ? "UNKNOWN" : type);
            processed.setOrderNo(orderNo);
            processed.setProcessedTime(LocalDateTime.now());
            processedEventMapper.insert(processed);
        } catch (Exception e) {
            log.error("[OrderConsumer] Failed to process message", e);
            throw new RuntimeException("Order event processing failed, trigger retry", e);
        }
    }
}
