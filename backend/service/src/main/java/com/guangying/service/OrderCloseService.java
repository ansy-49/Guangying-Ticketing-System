package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.common.constants.MQConstants;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.dao.mapper.SeatLockMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.model.dto.LockSeatsDTO;
import com.guangying.domain.model.po.OrderPO;
import com.guangying.domain.model.po.SeatLockPO;
import com.guangying.service.cache.SeatLayoutCacheService;
import com.guangying.service.infrastructure.OutboxService;
import com.guangying.service.infrastructure.QueueService;
import com.guangying.service.infrastructure.SeatLockScriptService;
import com.guangying.service.infrastructure.TransactionCallbacks;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一订单关闭用例。
 *
 * <p>延时消息、支付懒过期、数据库扫描和用户取消最终都进入同一条 CAS 关单链路，
 * 防止不同触发器各自维护库存返还、锁释放和 Outbox 逻辑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCloseService {

    public static final String REASON_USER_CANCEL = "USER_CANCEL";
    public static final String REASON_DELAY_MESSAGE = "DELAY_MESSAGE";
    public static final String REASON_PAYMENT_LAZY_EXPIRE = "PAYMENT_LAZY_EXPIRE";
    public static final String REASON_DB_SCAN_FALLBACK = "DB_SCAN_FALLBACK";

    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final SeatLockMapper seatLockMapper;
    private final OutboxService outboxService;
    private final SeatLockScriptService lockScriptService;
    private final QueueService queueService;
    private final TransactionCallbacks transactionCallbacks;
    private final SeatLayoutCacheService seatLayoutCacheService;

    @Transactional(rollbackFor = Exception.class, timeout = 8)
    public CloseResult cancelByUser(Long userId, String orderNo) {
        OrderPO order = orderMapper.selectOne(new LambdaQueryWrapper<OrderPO>()
                .eq(OrderPO::getOrderNo, orderNo)
                .eq(OrderPO::getUserId, userId)
                .eq(OrderPO::getDeleted, 0));
        if (order == null) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND.getCode(), "订单不存在");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING.getCode()) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "当前订单状态不允许取消");
        }
        CloseResult result = closePending(order, REASON_USER_CANCEL);
        if (result != CloseResult.CLOSED) {
            throw new BizException(ResponseCodeEnum.CONFLICT.getCode(), "订单状态已变化，请刷新后重试");
        }
        return result;
    }

    /**
     * 幂等关闭过期订单。消息可能重复、扫描可能多实例重叠，最终由状态条件更新裁决。
     */
    @Transactional(rollbackFor = Exception.class, timeout = 8)
    public CloseResult closeExpired(String orderNo, String reason) {
        OrderPO order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return CloseResult.NOT_FOUND;
        }
        if (order.getStatus() != OrderStatusEnum.PENDING.getCode()) {
            return CloseResult.ALREADY_TERMINAL;
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getExpireTime() != null && now.isBefore(order.getExpireTime())) {
            return CloseResult.NOT_DUE;
        }
        return closePending(order, reason);
    }

    private CloseResult closePending(OrderPO order, String reason) {
        LocalDateTime now = LocalDateTime.now();
        int closed = orderMapper.closePendingOrder(order.getOrderNo(), now, reason);
        if (closed == 0) {
            return CloseResult.ALREADY_TERMINAL;
        }

        List<LockSeatsDTO.SeatPos> positions = loadSeatPositions(order.getOrderNo());
        int stockReturned = scheduleMapper.rollbackStock(order.getScheduleId(), order.getSeatCount());
        if (stockReturned != 1) {
            throw new IllegalStateException("Failed to return schedule stock: " + order.getOrderNo());
        }
        seatLockMapper.releaseOrderLocks(order.getOrderNo());

        if (order.getSeatCount() != null && positions.size() != order.getSeatCount()) {
            log.warn("[OrderClose] Seat-lock count mismatch: orderNo={}, expected={}, actual={}",
                    order.getOrderNo(), order.getSeatCount(), positions.size());
        }

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("type", MQConstants.TAG_ORDER_CANCELLED);
        eventPayload.put("orderNo", order.getOrderNo());
        eventPayload.put("userId", order.getUserId());
        eventPayload.put("scheduleId", order.getScheduleId());
        eventPayload.put("seatCount", order.getSeatCount());
        eventPayload.put("reason", reason);
        eventPayload.put("timestamp", System.currentTimeMillis());
        outboxService.writeEvent(MQConstants.TAG_ORDER_CANCELLED, eventPayload);

        transactionCallbacks.afterCommit(() -> {
            if (!positions.isEmpty()) {
                try {
                    lockScriptService.releaseSeats(
                            order.getScheduleId(), positions, order.getUserId());
                } catch (Exception e) {
                    log.warn("[OrderClose] Failed to release Redis seat locks: orderNo={}",
                            order.getOrderNo(), e);
                }
            }
            try {
                queueService.leave(order.getScheduleId(), order.getUserId());
            } catch (Exception e) {
                log.warn("[OrderClose] Failed to leave waiting room: orderNo={}",
                        order.getOrderNo(), e);
            }
            seatLayoutCacheService.invalidate(order.getScheduleId());
        });

        log.info("[OrderClose] Closed: orderNo={}, reason={}, seatsReturned={}",
                order.getOrderNo(), reason, order.getSeatCount());
        return CloseResult.CLOSED;
    }

    private List<LockSeatsDTO.SeatPos> loadSeatPositions(String orderNo) {
        List<SeatLockPO> locks = seatLockMapper.selectLocksByOrderNo(orderNo);
        if (locks == null) {
            return List.of();
        }
        return locks.stream().map(lock -> {
            LockSeatsDTO.SeatPos pos = new LockSeatsDTO.SeatPos();
            pos.setRow(lock.getRowNum());
            pos.setCol(lock.getColNum());
            return pos;
        }).toList();
    }

    public enum CloseResult {
        CLOSED,
        ALREADY_TERMINAL,
        NOT_DUE,
        NOT_FOUND
    }
}
