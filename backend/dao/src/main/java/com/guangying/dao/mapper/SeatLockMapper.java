package com.guangying.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guangying.domain.model.po.SeatLockPO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 座位锁定 Mapper — 高并发场景核心 DAO
 */
@Mapper
public interface SeatLockMapper extends BaseMapper<SeatLockPO> {

    /**
     * 查询某场次的所有有效锁定（未过期 + 状态为锁定/已购买）
     */
    @Select("SELECT * FROM seat_lock WHERE schedule_id = #{scheduleId} AND ((status = 1 AND lock_until > #{now}) OR status = 2)")
    List<SeatLockPO> selectActiveLocks(@Param("scheduleId") Long scheduleId, @Param("now") LocalDateTime now);

    /**
     * 查询场次指定座位的有效锁定
     */
    @Select("SELECT * FROM seat_lock WHERE schedule_id = #{scheduleId} AND row_num = #{row} AND col_num = #{col} AND ((status = 1 AND lock_until > #{now}) OR status = 2) LIMIT 1")
    SeatLockPO selectActiveLock(@Param("scheduleId") Long scheduleId, @Param("row") int row, @Param("col") int col, @Param("now") LocalDateTime now);

    /**
     * 释放用户在某场次的所有锁定座位（新架构下 lock 已绑定 order_no，不再过滤 NULL）
     */
    @Delete("DELETE FROM seat_lock WHERE schedule_id = #{scheduleId} AND user_id = #{userId} AND status = 1")
    int releaseUserLocks(@Param("scheduleId") Long scheduleId, @Param("userId") Long userId);

    /**
     * 将锁定状态改为已购买
     */
    @Update("UPDATE seat_lock SET status = 2, update_time = #{now} WHERE order_no = #{orderNo} AND status = 1")
    int markAsPurchased(@Param("orderNo") String orderNo, @Param("now") LocalDateTime now);

    /**
     * 查询用户在某场次锁定的座位
     */
    @Select("SELECT * FROM seat_lock WHERE schedule_id = #{scheduleId} AND user_id = #{userId} AND status = 1 AND lock_until > #{now}")
    List<SeatLockPO> selectUserLocks(@Param("scheduleId") Long scheduleId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM seat_lock WHERE schedule_id = #{scheduleId} AND user_id = #{userId} AND lock_token = #{lockToken} AND status = 1 AND lock_until > #{now}")
    List<SeatLockPO> selectActiveLocksByToken(@Param("scheduleId") Long scheduleId,
                                              @Param("userId") Long userId,
                                              @Param("lockToken") String lockToken,
                                              @Param("now") LocalDateTime now);

    @Update("UPDATE seat_lock SET order_no = #{orderNo}, lock_until = #{lockUntil}, update_time = #{now} WHERE schedule_id = #{scheduleId} AND user_id = #{userId} AND lock_token = #{lockToken} AND status = 1 AND lock_until > #{now}")
    int bindLocksToOrder(@Param("scheduleId") Long scheduleId,
                         @Param("userId") Long userId,
                         @Param("lockToken") String lockToken,
                         @Param("orderNo") String orderNo,
                         @Param("lockUntil") LocalDateTime lockUntil,
                         @Param("now") LocalDateTime now);

    @Select("SELECT * FROM seat_lock WHERE order_no = #{orderNo} AND status IN (1, 2)")
    List<SeatLockPO> selectLocksByOrderNo(@Param("orderNo") String orderNo);

    @Delete("DELETE FROM seat_lock WHERE order_no = #{orderNo} AND status = 1")
    int releaseOrderLocks(@Param("orderNo") String orderNo);

    /**
     * 清理过期锁定（定时任务/启动时调用）
     */
    @Delete("DELETE FROM seat_lock WHERE status = 1 AND order_no IS NULL AND lock_until <= #{now}")
    int cleanExpiredLocks(@Param("now") LocalDateTime now);
}
