package com.guangying.provider.controller;

import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.common.enums.RateLimitDimension;
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
 * 用户请求 → @RateLimit 分层令牌桶（用户 + 场次 + 全局）
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
     * <p>限流策略：用户级防重复提交，场次级吸收单热点洪峰，全局级保护锁座集群。</p>
     */
    @PostMapping("/lock")
    @RateLimit(key = "seat:lock", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            capacity = 8, refillRate = 2)
    @RateLimit(key = "seat:lock", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            dimension = RateLimitDimension.RESOURCE, dimensionKey = "#dto.scheduleId",
            capacity = 400, refillRate = 120)
    @RateLimit(key = "seat:lock", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            dimension = RateLimitDimension.GLOBAL, capacity = 1200, refillRate = 400)
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

}
