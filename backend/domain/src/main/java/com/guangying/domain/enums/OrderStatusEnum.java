package com.guangying.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态枚举（状态机）
 * <pre>
 *  PENDING(0) ──→ PAID(1)
 *      │               │
 *      ↓               ↓
 *  CANCELLED(2)   REFUNDED(3)
 * </pre>
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    PENDING(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消"),
    REFUNDED(3, "已退款");

    private final int code;
    private final String desc;

    public static OrderStatusEnum of(int code) {
        for (OrderStatusEnum s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
}
