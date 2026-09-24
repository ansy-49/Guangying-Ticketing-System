package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.OrderPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper（MyBatis XML 高级映射）
 */
public interface OrderMapper extends BaseMapper<OrderPO> {

    /**
     * 根据用户ID查询订单列表（分页用 MyBatis-Plus Page 即可）
     */
    List<OrderPO> selectByUserIdWithPage(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT * FROM ticket_order WHERE status = 0 AND expire_time <= #{now} AND deleted = 0 LIMIT #{limit}")
    List<OrderPO> selectExpiredPendingOrders(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE ticket_order SET status = 2, cancel_time = #{now}, update_time = CURRENT_TIMESTAMP WHERE order_no = #{orderNo} AND status = 0 AND deleted = 0")
    int closePendingOrder(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now);

    @Update("UPDATE ticket_order SET status = 1, pay_time = #{now}, update_time = CURRENT_TIMESTAMP WHERE order_no = #{orderNo} AND status = 0 AND deleted = 0")
    int markOrderPaid(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now);
}
