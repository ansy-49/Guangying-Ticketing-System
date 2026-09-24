package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单座位明细 — 一对多：一个订单对应多个座位
 */
@Data
@TableName("order_seat")
public class OrderSeatPO implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单编号 */
    private String orderNo;

    /** 场次ID */
    private Long scheduleId;

    /** 行号 */
    private Integer rowNum;

    /** 列号 */
    private Integer colNum;

    /** 座位标签(如"5排3座") */
    private String seatLabel;

    private LocalDateTime createTime;
}
