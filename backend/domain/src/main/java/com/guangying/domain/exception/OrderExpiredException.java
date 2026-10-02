package com.guangying.domain.exception;

import com.guangying.domain.enums.ResponseCodeEnum;

/**
 * 订单已过期异常。
 *
 * <p>支付入口发现订单过期时，需要先提交关闭订单、回补库存和 Outbox 事件，
 * 再向调用方返回过期提示，因此该异常不会触发支付事务回滚。</p>
 */
public class OrderExpiredException extends BizException {

    public OrderExpiredException() {
        super(ResponseCodeEnum.SEAT_LOCK_EXPIRED);
    }
}
