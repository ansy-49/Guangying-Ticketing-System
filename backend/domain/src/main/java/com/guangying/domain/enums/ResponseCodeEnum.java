package com.guangying.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应码枚举
 */
@Getter
@AllArgsConstructor
public enum ResponseCodeEnum {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    RATE_LIMITED(429, "请求过于频繁，请稍后再试"),
    QUEUE_FULL(430, "系统繁忙，排队人数过多，请稍后再试"),
    STOCK_NOT_ENOUGH(460, "库存不足"),
    SEAT_LOCKED(462, "所选座位已被他人锁定"),
    SEAT_LOCK_EXPIRED(463, "锁座已过期，请重新选座"),
    ORDER_CREATE_FAILED(461, "下单失败，请重试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;
}
