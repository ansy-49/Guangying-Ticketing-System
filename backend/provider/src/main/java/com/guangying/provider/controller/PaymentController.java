package com.guangying.provider.controller;

import com.guangying.common.annotation.RateLimit;
import com.guangying.common.enums.RateLimitAlgorithm;
import com.guangying.common.enums.RateLimitDimension;
import com.guangying.domain.model.vo.OrderVO;
import com.guangying.domain.model.vo.Result;
import com.guangying.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 支付接口 — 模拟支付网关
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 模拟支付
     */
    @PostMapping("/pay")
    @RateLimit(key = "payment:pay", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            capacity = 6, refillRate = 2)
    @RateLimit(key = "payment:pay", algorithm = RateLimitAlgorithm.TOKEN_BUCKET,
            dimension = RateLimitDimension.GLOBAL, capacity = 600, refillRate = 200)
    public Result<OrderVO> pay(
            @RequestParam String orderNo,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        return Result.ok(paymentService.payOrder(userId, orderNo));
    }

    /**
     * 查询订单详情（含座位信息）
     */
    @GetMapping("/orderDetail")
    public Result<OrderVO> getOrderDetail(
            @RequestParam String orderNo,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        return Result.ok(paymentService.getOrderDetail(userId, orderNo));
    }
}
