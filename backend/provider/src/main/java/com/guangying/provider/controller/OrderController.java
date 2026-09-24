package com.guangying.provider.controller;

import com.guangying.domain.model.vo.OrderVO;
import com.guangying.domain.model.vo.Result;
import com.guangying.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单接口（需要登录）— 目标架构简化版
 *
 * <p>建单已合并到 POST /api/seat/lock（SeatController），
 * 本控制器只负责：取消订单、查询订单。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 取消订单
     */
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancelOrder(@PathVariable String orderNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        orderService.cancelOrder(userId, orderNo);
        return Result.ok(null);
    }

    /**
     * 查询用户订单列表
     */
    @GetMapping("/list")
    public Result<List<OrderVO>> getUserOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return Result.fail(401, "请先登录");
        }
        return Result.ok(orderService.getUserOrders(userId, page, size));
    }
}
