package com.guangying.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.dao.mapper.ProcessedEventMapper;
import com.guangying.domain.model.po.ProcessedEventPO;
import com.guangying.service.mq.handler.OrderEventHandler;
import com.guangying.service.mq.handler.OrderEventHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 普通订单事件和超时定时事件共享的解析、幂等与命令路由器。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProcessor {

    private final ObjectMapper objectMapper;
    private final OrderEventHandlerRegistry handlerRegistry;
    private final ProcessedEventMapper processedEventMapper;

    @SuppressWarnings("unchecked")
    @Transactional(rollbackFor = Exception.class)
    public void process(String message) {
        try {
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String type = (String) event.get("type");
            String orderNo = (String) event.get("orderNo");
            String eventId = (String) event.get("eventId");

            if (type == null || type.isBlank() || orderNo == null || orderNo.isBlank()) {
                throw new IllegalArgumentException("Order event is missing type or orderNo");
            }
            if (eventId == null || eventId.isBlank()) {
                eventId = type + ":" + orderNo;
            }
            OrderEventHandler handler = handlerRegistry.find(type)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown order event type: " + type));

            ProcessedEventPO processed = new ProcessedEventPO();
            processed.setEventId(eventId);
            processed.setEventType(type);
            processed.setOrderNo(orderNo);
            processed.setProcessedTime(LocalDateTime.now());
            try {
                processedEventMapper.insert(processed);
            } catch (DuplicateKeyException duplicate) {
                log.debug("[OrderEvent] Duplicate skipped: eventId={}", eventId);
                return;
            }

            log.info("[OrderEvent] Processing: eventId={}, type={}, orderNo={}",
                    eventId, type, orderNo);
            handler.handle(event);
        } catch (Exception e) {
            log.error("[OrderEvent] Processing failed", e);
            throw new RuntimeException("Order event processing failed, trigger retry", e);
        }
    }
}
