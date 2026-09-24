package com.guangying.service.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.OutboxEventMapper;
import com.guangying.domain.model.po.OutboxEventPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地 Outbox 服务 — 保证事件最终投递
 *
 * <h3>设计要点：</h3>
 * <ol>
 *   <li>在业务事务内 writeEvent，与业务数据原子落地</li>
 *   <li>事务提交后，定时轮询 PENDING 事件 → 发 MQ → 标记 SENT</li>
 *   <li>RocketMQ 宕机时事件躺在 outbox 表中，恢复后自动补发</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventMapper outboxEventMapper;

    @Autowired(required = false)
    private RocketMQTemplate rocketMQTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 在业务事务内调用 — 写入 outbox 事件
     */
    public void writeEvent(String eventType, Object payload) {
        try {
            OutboxEventPO event = new OutboxEventPO();
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setStatus("PENDING");
            event.setRetries(0);
            event.setCreateTime(LocalDateTime.now());
            outboxEventMapper.insert(event);
            log.debug("[Outbox] Event written: type={}", eventType);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] Failed to serialize event payload: type={}", eventType, e);
        }
    }

    /**
     * 定时轮询并投递 PENDING 事件到 MQ（每 5 秒）
     */
    @Scheduled(fixedDelay = 5000)
    public void pollAndSend() {
        if (rocketMQTemplate == null) {
            return;
        }

        List<OutboxEventPO> pending = outboxEventMapper.selectPending(100);
        if (pending.isEmpty()) {
            return;
        }

        for (OutboxEventPO event : pending) {
            try {
                String topic = resolveTopic(event.getEventType());
                String tag = event.getEventType();
                rocketMQTemplate.syncSend(topic + ":" + tag, event.getPayload(), 1000);
                outboxEventMapper.markSent(event.getId(), LocalDateTime.now());
                log.debug("[Outbox] Event sent: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                outboxEventMapper.markFailed(event.getId());
                log.error("[Outbox] Failed to send event: id={}, type={}", event.getId(), event.getEventType(), e);
            }
        }
    }

    private String resolveTopic(String eventType) {
        if (eventType.startsWith("ORDER_")) {
            return MQConstants.ORDER_TOPIC;
        }
        return MQConstants.ORDER_TOPIC; // default
    }

    /**
     * 清理 7 天前已发送的事件（每日凌晨）
     */
    @Scheduled(cron = "0 10 3 * * ?")
    public void cleanSent() {
        int deleted = outboxEventMapper.deleteSentBefore(LocalDateTime.now().minusDays(7));
        if (deleted > 0) {
            log.info("[Outbox] Cleaned {} sent events older than 7 days", deleted);
        }
    }
}
