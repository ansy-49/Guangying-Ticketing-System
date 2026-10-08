package com.guangying.provider;

import com.guangying.dao.mapper.OutboxEventMapper;
import com.guangying.domain.model.po.OutboxEventPO;
import com.guangying.service.infrastructure.OutboxService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxTimerDispatchTest {

    @Test
    void timeoutOutboxEventUsesRocketMqAbsoluteDeliveryTime() {
        OutboxEventMapper mapper = mock(OutboxEventMapper.class);
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        OutboxService service = new OutboxService(mapper);
        ReflectionTestUtils.setField(service, "rocketMQTemplate", template);
        ReflectionTestUtils.setField(service, "claimSeconds", 30);
        ReflectionTestUtils.setField(service, "maxRetries", 10);

        long deliverAt = System.currentTimeMillis() + 60_000;
        OutboxEventPO event = new OutboxEventPO();
        event.setId(1L);
        event.setEventId("evt-timeout-1");
        event.setEventType("ORDER_TIMEOUT_CHECK");
        event.setStatus("PENDING");
        event.setRetries(0);
        event.setPayload("{\"type\":\"ORDER_TIMEOUT_CHECK\",\"orderNo\":\"GY1\","
                + "\"deliverAtEpochMs\":" + deliverAt + "}");

        when(mapper.selectClaimable(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(event));
        when(mapper.tryClaim(eq(1L), anyString(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(mapper.markSent(eq(1L), anyString(), any(LocalDateTime.class))).thenReturn(1);

        service.pollAndSend();

        verify(template).syncSendDeliverTimeMills(
                "guangying_order_timeout:ORDER_TIMEOUT_CHECK", event.getPayload(), deliverAt);
        verify(mapper).markSent(eq(1L), anyString(), any(LocalDateTime.class));
    }
}
