package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guangying.domain.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单持久化对象
 *
 * <p>订单状态机: 待支付(0) → 已支付(1) / 已取消(2) → 已退款(3)</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_order")
public class OrderPO extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单编号（分布式唯一） */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 建单幂等键，同一用户下唯一 */
    private String idempotencyKey;

    /** 场次与座位集合的稳定摘要，用于识别幂等键误复用 */
    private String requestFingerprint;

    /** 场次ID */
    private Long scheduleId;

    private String lockToken;

    /** 电影名（冗余快照） */
    private String movieName;

    /** 影院名（冗余快照） */
    private String cinemaName;

    /** 影厅名（冗余快照） */
    private String hallName;

    /** 放映时间（冗余快照） */
    private String showTime;

    /** 座位数 */
    private Integer seatCount;

    /** 座位信息(冗余:如 5排3座,5排4座) */
    private String seatsInfo;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 总价 */
    private BigDecimal totalPrice;

    /**
     * 订单状态
     * <ul>
     *   <li>0 - 待支付</li>
     *   <li>1 - 已支付</li>
     *   <li>2 - 已取消</li>
     *   <li>3 - 已退款</li>
     * </ul>
     */
    private Integer status;

    /** 支付截止时间 */
    private LocalDateTime expireTime;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 取消时间 */
    private LocalDateTime cancelTime;
}
