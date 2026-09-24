package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.OrderSeatPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单座位明细 Mapper
 */
@Mapper
public interface OrderSeatMapper extends BaseMapper<OrderSeatPO> {

    /**
     * 查询某场次已购买的座位（状态为已支付的订单）
     */
    @Select("""
        SELECT os.* FROM order_seat os
        INNER JOIN ticket_order o ON os.order_id = o.id
        WHERE os.schedule_id = #{scheduleId} AND o.status = 1 AND o.deleted = 0
    """)
    List<OrderSeatPO> selectPurchasedSeats(@Param("scheduleId") Long scheduleId);

    /**
     * 查询某订单的所有座位
     */
    @Select("SELECT * FROM order_seat WHERE order_id = #{orderId}")
    List<OrderSeatPO> selectByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT * FROM order_seat WHERE order_no = #{orderNo}")
    List<OrderSeatPO> selectByOrderNo(@Param("orderNo") String orderNo);
}
