package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单展示对象
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderVO implements Serializable {

    private Long id;
    private String orderNo;
    private String lockToken;
    private String movieName;
    private String cinemaName;
    private String hallName;
    private String showTime;
    private Integer seatCount;
    private String seatsInfo;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    /** 0=待支付 1=已支付 2=已取消 3=已退款 */
    private Integer status;
    private String statusDesc;
    private String createTime;
    private String payTime;
    private String expireTime;
    private Long scheduleId;
    private String movieImg;
    /** 支付后剩余积分 */
    private Integer remainingPoints;
}
