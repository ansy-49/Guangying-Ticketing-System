package com.guangying.service.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.OutboxEventMapper;
import com.guangying.domain.model.po.OutboxEventPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Value("${guangying.outbox.max-retries:10}")
    private int maxRetries;

    @Value("${guangying.outbox.claim-seconds:30}")
    private int claimSeconds;

    /**
     * 在业务事务内调用 — 写入 outbox 事件
     */
    public void writeEvent(String eventType, Object payload) {
        String eventId = UUID.randomUUID().toString();
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            if (payload instanceof Map<?, ?> payloadMap) {
                payloadMap.forEach((key, value) -> envelope.put(String.valueOf(key), value));
            } else {
                envelope.put("data", payload);
            }
            envelope.put("eventId", eventId);
            envelope.putIfAbsent("type", eventType);

            OutboxEventPO event = new OutboxEventPO();
            event.setEventId(eventId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(envelope));
            event.setStatus("PENDING");
            event.setRetries(0);
            event.setCreateTime(LocalDateTime.now());
            outboxEventMapper.insert(event);
            log.debug("[Outbox] Event written: eventId={}, type={}", eventId, eventType);
        } catch (JsonProcessingException e) {
            log.error("[Outbox] Failed to serialize event payload: type={}", eventType, e);
            throw new IllegalStateException("Failed to persist outbox event", e);
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

        LocalDateTime now = LocalDateTime.now();
        List<OutboxEventPO> candidates = outboxEventMapper.selectClaimable(now, 100);
        if (candidates.isEmpty()) {
            return;
        }

        for (OutboxEventPO event : candidates) {
            String claimToken = UUID.randomUUID().toString();
            int claimed = outboxEventMapper.tryClaim(
                    event.getId(), claimToken, now, now.plusSeconds(claimSeconds));
            if (claimed == 0) {
                continue;
            }
            try {
                sendToBroker(event);
                outboxEventMapper.markSent(event.getId(), claimToken, LocalDateTime.now());
                log.debug("[Outbox] Event sent: eventId={}, type={}",
                        event.getEventId(), event.getEventType());
            } catch (Exception e) {
                int retry = event.getRetries() == null ? 1 : event.getRetries() + 1;
                long delaySeconds = Math.min(300L, 1L << Math.min(retry, 8));
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (error.length() > 1000) {
                    error = error.substring(0, 1000);
                }
                outboxEventMapper.markRetry(
                        event.getId(), claimToken, maxRetries,
                        LocalDateTime.now().plusSeconds(delaySeconds), error);
                log.error("[Outbox] Send failed: eventId={}, type={}, retry={}/{}",
                        event.getEventId(), event.getEventType(), retry, maxRetries, e);
            }
        }
    }

    private String resolveTopic(String eventType) {
        if (MQConstants.TAG_ORDER_TIMEOUT_CHECK.equals(eventType)) {
            return MQConstants.ORDER_TIMEOUT_TOPIC;
        }
        if (eventType.startsWith("ORDER_")) {
            return MQConstants.ORDER_TOPIC;
        }
        return MQConstants.ORDER_TOPIC; // default
    }

    /**
     * 普通领域事件立即发送；超时检查事件按绝对时间投递为 RocketMQ 5 定时消息。
     * 若 Outbox 晚于截止时间才恢复，则立即发送，由消费者再次校验数据库状态。
     */
    private void sendToBroker(OutboxEventPO event) throws JsonProcessingException {
        String destination = resolveTopic(event.getEventType()) + ":" + event.getEventType();
        if (!MQConstants.TAG_ORDER_TIMEOUT_CHECK.equals(event.getEventType())) {
            rocketMQTemplate.syncSend(destination, event.getPayload(), 1000);
            return;
        }

        JsonNode payload = objectMapper.readTree(event.getPayload());
        long deliverAt = payload.path("deliverAtEpochMs").asLong(0L);
        if (deliverAt <= 0L) {
            throw new IllegalStateException("Timeout event is missing deliverAtEpochMs");
        }
        if (deliverAt <= System.currentTimeMillis()) {
            rocketMQTemplate.syncSend(destination, event.getPayload(), 1000);
        } else {
            rocketMQTemplate.syncSendDeliverTimeMills(destination, event.getPayload(), deliverAt);
        }
    }

    public List<OutboxEventPO> listDead(int limit) {
        return outboxEventMapper.selectDead(Math.max(1, Math.min(limit, 200)));
    }

    public boolean retryDead(Long eventId) {
        return eventId != null
                && outboxEventMapper.requeueDead(eventId, LocalDateTime.now()) == 1;
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
