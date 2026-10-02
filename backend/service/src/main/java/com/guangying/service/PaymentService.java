package com.guangying.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.guangying.dao.mapper.OrderMapper;
import com.guangying.dao.mapper.OrderSeatMapper;
import com.guangying.dao.mapper.ScheduleMapper;
import com.guangying.dao.mapper.SeatLockMapper;
import com.guangying.dao.mapper.UserMapper;
import com.guangying.domain.enums.OrderStatusEnum;
import com.guangying.domain.enums.ResponseCodeEnum;
import com.guangying.domain.exception.BizException;
import com.guangying.domain.exception.OrderExpiredException;
import com.guangying.domain.model.dto.LockSeatsDTO;
import com.guangying.domain.model.po.OrderPO;
import com.guangying.domain.model.po.OrderSeatPO;
import com.guangying.domain.model.po.SeatLockPO;
import com.guangying.domain.model.po.UserPO;
import com.guangying.domain.model.vo.OrderVO;
import com.guangying.service.infrastructure.OutboxService;
import com.guangying.service.infrastructure.QueueService;
import com.guangying.service.infrastructure.SeatLockScriptService;
import com.guangying.service.infrastructure.SeatSoldService;
import com.guangying.service.infrastructure.TransactionCallbacks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付服务 — 目标架构：已售态写 DB（权威），Redis 更新只读投影
 *
 * <h3>支付链路：</h3>
 * <pre>
 * 令牌桶 + 幂等 + 懒过期 → CAS 扣积分 → orders WAIT_PAY→PAID → seat_lock status=1→2 → commit
 *   事务后 best-effort: SADD seat:sold, DEL seat:lock, 出票等下游逻辑（via outbox）
 * </pre>
 */
@Slf4j
@Service
public class PaymentService {

    private final OrderMapper orderMapper;
    private final SeatLockMapper seatLockMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final ScheduleMapper scheduleMapper;
    private final UserMapper userMapper;
    private final SeatLockScriptService lockScriptService;
    private final SeatSoldService soldService;
    private final OutboxService outboxService;
    private final QueueService queueService;
    private final TransactionCallbacks transactionCallbacks;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PaymentService(OrderMapper orderMapper,
                          SeatLockMapper seatLockMapper,
                          OrderSeatMapper orderSeatMapper,
                          ScheduleMapper scheduleMapper,
                          UserMapper userMapper,
                          SeatLockScriptService lockScriptService,
                          SeatSoldService soldService,
                          OutboxService outboxService,
                          QueueService queueService,
                          TransactionCallbacks transactionCallbacks) {
        this.orderMapper = orderMapper;
        this.seatLockMapper = seatLockMapper;
        this.orderSeatMapper = orderSeatMapper;
        this.scheduleMapper = scheduleMapper;
        this.userMapper = userMapper;
        this.lockScriptService = lockScriptService;
        this.soldService = soldService;
        this.outboxService = outboxService;
        this.queueService = queueService;
        this.transactionCallbacks = transactionCallbacks;
    }

    /**
     * 支付订单 — DB 强一致 + 懒过期
     */
    @Transactional(
            rollbackFor = Exception.class,
            noRollbackFor = OrderExpiredException.class,
            timeout = 8)
    public OrderVO payOrder(Long userId, String orderNo) {
        // 1. 查订单
        OrderPO order = selectUserOrder(userId, orderNo);
        if (order == null) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND.getCode(), "订单不存在");
        }

        // 2. 懒过期校验
        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() != OrderStatusEnum.PENDING.getCode()) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "订单状态不允许支付");
        }
        if (order.getExpireTime() != null && now.isAfter(order.getExpireTime())) {
            closeExpiredOrder(order, now);
            // closeExpiredOrder 的 DB 状态与 Outbox 必须先提交，再向调用方返回过期提示。
            throw new OrderExpiredException();
        }

        // 3. 验证锁座记录
        List<SeatLockPO> locks = seatLockMapper.selectLocksByOrderNo(orderNo);
        if (locks.size() != order.getSeatCount()) {
            throw new BizException(ResponseCodeEnum.SEAT_LOCK_EXPIRED);
        }

        // 4. CAS 扣积分
        int pointsCost = order.getTotalPrice().setScale(0, RoundingMode.HALF_UP).intValue();
        UserPO user = userMapper.selectById(userId);
        if (user == null || user.getPoints() == null || user.getPoints() < pointsCost) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(),
                    "积分不足，需要" + pointsCost + "积分，当前" + (user != null ? user.getPoints() : 0) + "积分");
        }
        int pointAffected = userMapper.deductPoints(userId, pointsCost);
        if (pointAffected == 0) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "积分扣减失败，请重试");
        }

        // 5. CAS 状态机推进 orders WAIT_PAY → PAID
        int paid = orderMapper.markOrderPaid(orderNo, now);
        if (paid == 0) {
            throw new BizException(ResponseCodeEnum.BAD_REQUEST.getCode(), "订单状态已变化，请刷新后重试");
        }

        // 6. 写入已售座位明细 + 座位落定（DB 权威！）
        List<LockSeatsDTO.SeatPos> seatPositions = new ArrayList<>();
        for (SeatLockPO lock : locks) {
            OrderSeatPO seat = new OrderSeatPO();
            seat.setOrderId(order.getId());
            seat.setOrderNo(orderNo);
            seat.setScheduleId(order.getScheduleId());
            seat.setRowNum(lock.getRowNum());
            seat.setColNum(lock.getColNum());
            seat.setSeatLabel(lock.getRowNum() + "排" + lock.getColNum() + "座");
            seat.setCreateTime(now);
            orderSeatMapper.insert(seat);

            seatPositions.add(new LockSeatsDTO.SeatPos());
            seatPositions.get(seatPositions.size() - 1).setRow(lock.getRowNum());
            seatPositions.get(seatPositions.size() - 1).setCol(lock.getColNum());
        }

        // seat_lock status=1→2（已售，DB 权威！）
        seatLockMapper.markAsPurchased(orderNo, now);

        // 7. 写 outbox（与业务同事务，保证事件最终发出）
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("type", "ORDER_PAID");
        eventPayload.put("orderNo", orderNo);
        eventPayload.put("userId", userId);
        eventPayload.put("scheduleId", order.getScheduleId());
        eventPayload.put("seatCount", order.getSeatCount());
        eventPayload.put("totalPrice", order.getTotalPrice());
        eventPayload.put("timestamp", System.currentTimeMillis());
        outboxService.writeEvent("ORDER_PAID", eventPayload);

        // 8. 数据库真正提交后再更新可重建的 Redis 投影。
        // Waiting Room 名额已经由锁座建单事务唯一负责释放，支付不再重复 leave。
        transactionCallbacks.afterCommit(
                () -> updateRedisProjection(order.getScheduleId(), seatPositions, userId));

        log.info("[Payment] Order paid: orderNo={}, userId={}, total={}, pointsAfter={}",
                orderNo, userId, order.getTotalPrice(), user.getPoints() - pointsCost);

        OrderVO vo = toVO(order);
        vo.setStatus(OrderStatusEnum.PAID.getCode());
        vo.setStatusDesc(OrderStatusEnum.PAID.getDesc());
        vo.setPayTime(now.format(FMT));
        UserPO updatedUser = userMapper.selectById(userId);
        vo.setRemainingPoints(updatedUser != null ? updatedUser.getPoints() : 0);
        return vo;
    }

    /**
     * 支付成功后更新 Redis 投影（best-effort，丢了对账重建）
     */
    private void updateRedisProjection(Long scheduleId, List<LockSeatsDTO.SeatPos> seats, Long userId) {
        try {
            // 释放锁 + 写入已售投影（Lua 脚本已包含 SADD，无需重复）
            lockScriptService.releaseLocksAndMarkSold(scheduleId, seats, userId);

            // 失效渲染缓存
            invalidateRenderCache(scheduleId);
        } catch (Exception e) {
            log.error("[Payment] Failed to update Redis projection: scheduleId={}", scheduleId, e);
        }
    }

    public OrderVO getOrderDetail(Long userId, String orderNo) {
        OrderPO order = selectUserOrder(userId, orderNo);
        if (order == null) {
            throw new BizException(ResponseCodeEnum.NOT_FOUND.getCode(), "订单不存在");
        }
        return toVO(order);
    }

    // =================== 内部方法 ===================

    private OrderPO selectUserOrder(Long userId, String orderNo) {
        LambdaQueryWrapper<OrderPO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderPO::getOrderNo, orderNo)
                .eq(OrderPO::getUserId, userId);
        return orderMapper.selectOne(wrapper);
    }

    private void closeExpiredOrder(OrderPO order, LocalDateTime now) {
        int closed = orderMapper.closePendingOrder(order.getOrderNo(), now);
        if (closed == 0) return;

        List<LockSeatsDTO.SeatPos> positions = loadSeatPositions(order.getOrderNo());

        scheduleMapper.rollbackStock(order.getScheduleId(), order.getSeatCount());
        seatLockMapper.releaseOrderLocks(order.getOrderNo());

        // outbox event
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("type", "ORDER_CANCELLED");
        eventPayload.put("orderNo", order.getOrderNo());
        eventPayload.put("scheduleId", order.getScheduleId());
        eventPayload.put("seatCount", order.getSeatCount());
        eventPayload.put("timestamp", System.currentTimeMillis());
        outboxService.writeEvent("ORDER_CANCELLED", eventPayload);

        transactionCallbacks.afterCommit(() -> {
            if (!positions.isEmpty()) {
                lockScriptService.releaseSeats(
                        order.getScheduleId(), positions, order.getUserId());
            }
            invalidateRenderCache(order.getScheduleId());
            queueService.leave(order.getScheduleId(), order.getUserId());
        });

        log.info("[Payment] Expired order closed: orderNo={}", order.getOrderNo());
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

    private void invalidateRenderCache(Long scheduleId) {
        // 渲染缓存自然过期即可（3-5s TTL），无需主动删除
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
            vo.setCreateTime(po.getCreateTime().format(FMT));
        }
        if (po.getPayTime() != null) {
            vo.setPayTime(po.getPayTime().format(FMT));
        }
        if (po.getExpireTime() != null) {
            vo.setExpireTime(po.getExpireTime().format(FMT));
        }
        return vo;
    }
}
