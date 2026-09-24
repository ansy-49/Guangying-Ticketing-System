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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final DateTimeFormatter VO_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public OrderService(OrderMapper orderMapper,
                        ScheduleMapper scheduleMapper,
                        SeatLockMapper seatLockMapper,
                        OutboxService outboxService,
                        SeatLockScriptService lockScriptService,
                        QueueService queueService) {
        this.orderMapper = orderMapper;
        this.scheduleMapper = scheduleMapper;
        this.seatLockMapper = seatLockMapper;
        this.outboxService = outboxService;
        this.lockScriptService = lockScriptService;
        this.queueService = queueService;
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
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<OrderPO> expired = orderMapper.selectExpiredPendingOrders(now, 100);
        if (expired.isEmpty()) return;

        for (OrderPO order : expired) {
            try {
                closePendingOrder(order, "TIMEOUT");
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

        // ★ 先释放 Redis 锁（DB 事务之前），防止座位被幽灵锁定
        releaseRedisLocks(order);

        // 回滚库存
        scheduleMapper.rollbackStock(order.getScheduleId(), order.getSeatCount());

        // 释放座位锁
        seatLockMapper.releaseOrderLocks(order.getOrderNo());

        // ★ 释放排队入场名额（热门场次准入推进）
        queueService.leave(order.getScheduleId(), order.getUserId());

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

        log.info("[Order] Closed: orderNo={}, reason={}, seatsReturned={}",
                order.getOrderNo(), reason, order.getSeatCount());
    }

    /**
     * 释放订单关联的 Redis 座位锁（best-effort）
     */
    private void releaseRedisLocks(OrderPO order) {
        try {
            List<SeatLockPO> locks = seatLockMapper.selectLocksByOrderNo(order.getOrderNo());
            if (locks != null && !locks.isEmpty()) {
                List<LockSeatsDTO.SeatPos> positions = locks.stream().map(lock -> {
                    LockSeatsDTO.SeatPos pos = new LockSeatsDTO.SeatPos();
                    pos.setRow(lock.getRowNum());
                    pos.setCol(lock.getColNum());
                    return pos;
                }).toList();
                lockScriptService.releaseSeats(order.getScheduleId(), positions, order.getUserId());
                log.info("[Order] Released {} Redis locks for orderNo={}", positions.size(), order.getOrderNo());
            }
        } catch (Exception e) {
            log.error("[Order] Failed to release Redis locks for orderNo={}", order.getOrderNo(), e);
        }
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
