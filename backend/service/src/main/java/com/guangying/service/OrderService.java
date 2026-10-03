package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.dao.mapper.SeatLockMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.model.dto.LockSeatsDTO;
import com.guangying.domain.model.po.OrderPO;
import com.guangying.domain.model.po.SeatLockPO;
import com.guangying.domain.model.vo.OrderVO;
import com.guangying.service.infrastructure.OutboxService;
import com.guangying.service.infrastructure.QueueService;
import com.guangying.service.infrastructure.SeatLockScriptService;
import com.guangying.service.infrastructure.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单服务 — 目标架构简化版
 *
 * <p>建单已合并到 SeatService.lockSeatsAndCreateOrder()。
 * 本服务只负责：取消订单、查询订单、超时关单兜底。</p>
 */
@Slf4j
@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final ScheduleMapper scheduleMapper;
    private final SeatLockMapper seatLockMapper;
    private final OutboxService outboxService;
    private final SeatLockScriptService lockScriptService;
    private final QueueService queueService;
    private final TransactionCallbacks transactionCallbacks;
    private final TransactionTemplate requiresNewTransaction;

    private static final DateTimeFormatter VO_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderService(OrderMapper orderMapper,
                        ScheduleMapper scheduleMapper,
                        SeatLockMapper seatLockMapper,
                        OutboxService outboxService,
                        SeatLockScriptService lockScriptService,
                        QueueService queueService,
                        TransactionCallbacks transactionCallbacks,
                        PlatformTransactionManager transactionManager) {
        this.orderMapper = orderMapper;
        this.scheduleMapper = scheduleMapper;
        this.seatLockMapper = seatLockMapper;
        this.outboxService = outboxService;
        this.lockScriptService = lockScriptService;
        this.queueService = queueService;
        this.transactionCallbacks = transactionCallbacks;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTransaction.setTimeout(8);
    }

    /**
     * 用户主动取消订单
     */
    @Transactional(rollbackFor = Exception.class, timeout = 8)
    public void cancelOrder(Long userId, String orderNo) {
        LambdaQueryWrapper<OrderPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderPO::getOrderNo, orderNo)
                .eq(OrderPO::getUserId, userId);
        OrderPO order = orderMapper.selectOne(wrapper);

        if (order == null) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND.getCode(), "订单不存在");
        }
        if (order.getStatus() != OrderStatusEnum.PENDING.getCode()) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "当前订单状态不允许取消");
        }

        closePendingOrder(order, "USER_CANCEL");
    }

    /**
     * 定时兜底：关闭已过期的待支付订单（每 60s）
     *
     * <p>正常情况延时消息已处理，这里是兜底</p>
     */
    @Scheduled(fixedDelay = 60000)
    public void cancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderPO> expired = orderMapper.selectExpiredPendingOrders(now, 100);
        if (expired.isEmpty()) return;

        for (OrderPO order : expired) {
            try {
                requiresNewTransaction.executeWithoutResult(
                        status -> closePendingOrder(order, "TIMEOUT"));
            } catch (Exception e) {
                log.error("[Order] Failed to close expired order: orderNo={}", order.getOrderNo(), e);
            }
        }
    }

    /**
     * 查询用户订单列表
     */
    public List<OrderVO> getUserOrders(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        return orderMapper.selectByUserIdWithPage(userId, offset, size).stream()
                .map(this::toVO)
                .toList();
    }

    // =================== 内部方法 ===================

    /**
     * 关闭待支付订单 — 回滚库存 + 释放锁 + outbox 事件
     */
    private void closePendingOrder(OrderPO order, String reason) {
        LocalDateTime now = LocalDateTime.now();
        int closed = orderMapper.closePendingOrder(order.getOrderNo(), now);
        if (closed == 0) return;

        List<LockSeatsDTO.SeatPos> positions = loadSeatPositions(order.getOrderNo());

        // 回滚库存
        scheduleMapper.rollbackStock(order.getScheduleId(), order.getSeatCount());

        // 释放座位锁
        seatLockMapper.releaseOrderLocks(order.getOrderNo());

        // outbox 事件
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("type", "ORDER_CANCELLED");
        eventPayload.put("orderNo", order.getOrderNo());
        eventPayload.put("userId", order.getUserId());
        eventPayload.put("scheduleId", order.getScheduleId());
        eventPayload.put("seatCount", order.getSeatCount());
        eventPayload.put("reason", reason);
        eventPayload.put("timestamp", System.currentTimeMillis());
        outboxService.writeEvent("ORDER_CANCELLED", eventPayload);

        transactionCallbacks.afterCommit(() -> {
            if (!positions.isEmpty()) {
                lockScriptService.releaseSeats(
                        order.getScheduleId(), positions, order.getUserId());
            }
            queueService.leave(order.getScheduleId(), order.getUserId());
        });

        log.info("[Order] Closed: orderNo={}, reason={}, seatsReturned={}",
                order.getOrderNo(), reason, order.getSeatCount());
    }

    private List<LockSeatsDTO.SeatPos> loadSeatPositions(String orderNo) {
        List<SeatLockPO> locks = seatLockMapper.selectLocksByOrderNo(orderNo);
        if (locks == null) return List.of();
        return locks.stream().map(lock -> {
            LockSeatsDTO.SeatPos pos = new LockSeatsDTO.SeatPos();
            pos.setRow(lock.getRowNum());
            pos.setCol(lock.getColNum());
            return pos;
        }).toList();
    }

    private OrderVO toVO(OrderPO po) {
        OrderVO vo = new OrderVO();
        vo.setId(po.getId());
        vo.setOrderNo(po.getOrderNo());
        vo.setLockToken(po.getLockToken());
        vo.setMovieName(po.getMovieName());
        vo.setCinemaName(po.getCinemaName());
        vo.setHallName(po.getHallName());
        vo.setShowTime(po.getShowTime());
        vo.setSeatCount(po.getSeatCount());
        vo.setSeatsInfo(po.getSeatsInfo());
        vo.setUnitPrice(po.getUnitPrice());
        vo.setTotalPrice(po.getTotalPrice());
        vo.setStatus(po.getStatus());
        vo.setStatusDesc(OrderStatusEnum.of(po.getStatus()).getDesc());
        vo.setScheduleId(po.getScheduleId());
        if (po.getCreateTime() != null) {
            vo.setCreateTime(po.getCreateTime().format(VO_TIME_FMT));
        }
        if (po.getPayTime() != null) {
            vo.setPayTime(po.getPayTime().format(VO_TIME_FMT));
        }
        if (po.getExpireTime() != null) {
            vo.setExpireTime(po.getExpireTime().format(VO_TIME_FMT));
        }
        return vo;
    }
}
