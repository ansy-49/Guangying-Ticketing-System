package com.guangying.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 支付订单请求
 */
@Data
public class PayOrderDTO implements Serializable {

    /** 订单编号 */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /** 支付方式: alipay/wechat/bank */
    private String payMethod;
}
