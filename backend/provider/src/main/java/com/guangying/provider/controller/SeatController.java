package com.guangying.provider.controller;

import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.domain.model.dto.LockSeatsDTO;
import com.guangying.domain.model.vo.OrderVO;
import com.guangying.domain.model.vo.Result;
import com.guangying.domain.model.vo.SeatLayoutVO;
import com.guangying.service.SeatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 座位接口 — 目标架构：Lua 原子锁 + 同步建单，直接返回 orderNo
 *
 * <h3>架构：</h3>
 * <pre>
 * 用户请求 → @RateLimit 令牌桶（用户维度）
 *          → Redis Lua 原子锁座（争抢在此终结）
 *          → DB 事务建单（INSERT seat_lock + orders）
 *          → 返回 orderNo → 前端跳支付页
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/seat")
public class SeatController {

    private final SeatService seatService;

    public SeatController(SeatService seatService) {
        this.seatService = seatService;
    }

    /**
     * 获取影厅座位布局（含实时锁定/已售状态）
     */
    @GetMapping("/layout")
    public Result<SeatLayoutVO> getSeatLayout(
            @RequestParam Long scheduleId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(seatService.getSeatLayout(scheduleId, userId != null ? userId : 0L));
    }

    /**
     * 锁座 + 建单 — 一个请求完成，返回 orderNo 直接跳支付
     *
     * <p>限流策略：令牌桶算法，桶容量5（最大突发），每秒补充2个令牌</p>
     */
    @PostMapping("/lock")
    @RateLimit(key = "seat:lock", algorithm = RateLimitAlgorithm.TOKEN_BUCKET, capacity = 5, refillRate = 2)
    public Result<OrderVO> lockSeats(
            @Valid @RequestBody LockSeatsDTO dto,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }

        OrderVO orderVO = seatService.lockSeatsAndCreateOrder(userId, dto);
        return Result.ok(orderVO);
    }

    /**
     * 释放座位锁定
     */
    @PostMapping("/unlock")
    public Result<Void> unlockSeats(
            @RequestParam Long scheduleId,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        seatService.unlockSeats(userId, scheduleId);
        return Result.ok(null);
    }
}
