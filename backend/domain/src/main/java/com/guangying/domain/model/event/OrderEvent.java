package com.guangying.domain.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单事件 - 通过 MQ 异步处理后续逻辑（缓存失效、通知等）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent implements Serializable {

    public enum Type { CREATED, PAID, CANCELLED, REFUNDED }

    private Type type;
    private String orderNo;
    private Long userId;
    private Long scheduleId;
    private Long movieId;
    private String movieName;
    private Integer seatCount;
    private BigDecimal totalPrice;
    private long timestamp;
}
