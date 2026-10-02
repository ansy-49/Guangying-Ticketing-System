package com.guangying.provider;

import com.guangying.dao.mapper.OutboxEventMapper;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.exception.OrderExpiredException;
import com.guangying.domain.model.po.OrderPO;
import com.guangying.domain.model.po.OutboxEventPO;
import com.guangying.service.PaymentService;
import com.guangying.service.infrastructure.OutboxService;
import com.guangying.service.infrastructure.TransactionCallbacks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:guangying-reliability;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
class ReliabilityHardeningIntegrationTest {

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private OutboxEventMapper outboxEventMapper;

    @Autowired
    private TransactionCallbacks transactionCallbacks;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderMapper orderMapper;

    @Test
    void outboxEventCanOnlyBeClaimedByOneInstance() {
        outboxService.writeEvent("ORDER_CREATED", Map.of("orderNo", "claim-test"));
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEventPO> events = outboxEventMapper.selectClaimable(now.plusSeconds(1), 20);
        OutboxEventPO event = events.stream()
                .filter(item -> item.getPayload().contains("claim-test"))
                .findFirst()
                .orElseThrow();

        String firstClaim = UUID.randomUUID().toString();
        int first = outboxEventMapper.tryClaim(
                event.getId(), firstClaim, now, now.plusSeconds(30));
        int second = outboxEventMapper.tryClaim(
                event.getId(), UUID.randomUUID().toString(), now, now.plusSeconds(30));

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, outboxEventMapper.markRetry(
                event.getId(), firstClaim, 10, now.minusSeconds(1), "test retry"));
        assertTrue(outboxEventMapper.selectClaimable(now, 20).stream()
                .anyMatch(item -> item.getId().equals(event.getId())));
    }

    @Test
    void externalCallbacksFollowRealTransactionOutcome() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        AtomicBoolean committed = new AtomicBoolean();
        AtomicBoolean rolledBack = new AtomicBoolean();

        template.executeWithoutResult(status -> transactionCallbacks.register(
                () -> committed.set(true),
                () -> rolledBack.set(true)));
        assertTrue(committed.get());
        assertFalse(rolledBack.get());

        committed.set(false);
        rolledBack.set(false);
        template.executeWithoutResult(status -> {
            transactionCallbacks.register(
                    () -> committed.set(true),
                    () -> rolledBack.set(true));
            status.setRollbackOnly();
        });
        assertFalse(committed.get());
        assertTrue(rolledBack.get());
    }

    @Test
    void expiredPaymentCommitsCloseAndOutboxBeforeReturningError() {
        String orderNo = "expired-" + UUID.randomUUID();
        OrderPO order = new OrderPO();
        order.setOrderNo(orderNo);
        order.setUserId(9_999L);
        order.setScheduleId(1L);
        order.setSeatCount(1);
        order.setStatus(OrderStatusEnum.PENDING.getCode());
        order.setExpireTime(LocalDateTime.now().minusMinutes(1));
        orderMapper.insert(order);

        assertThrows(OrderExpiredException.class,
                () -> paymentService.payOrder(order.getUserId(), orderNo));

        OrderPO closed = orderMapper.selectById(order.getId());
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), closed.getStatus());
        assertTrue(outboxEventMapper.selectClaimable(LocalDateTime.now().plusSeconds(1), 100)
                .stream()
                .anyMatch(event -> event.getPayload().contains(orderNo)));
    }
}
