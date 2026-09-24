package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 座位锁定实体 — 高并发锁座核心（面试重点）
 *
 * <h3>设计要点：</h3>
 * <ul>
 *   <li>schedule_id + row_num + col_num + status 组成唯一约束，防止同一座位被重复锁定</li>
 *   <li>lock_until 记录锁定到期时间（通常15分钟），超时自动释放</li>
 *   <li>status 状态机：1(锁定中) → 2(已购买) 或 1(锁定中) → 0(已释放/超时)</li>
 *   <li>配合 Redis 分布式锁使用，DB 锁记录作为最终一致性保障</li>
 * </ul>
 */
@Data
@TableName("seat_lock")
public class SeatLockPO implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场次ID */
    private Long scheduleId;

    /** 行号 (1-based) */
    private Integer rowNum;

    /** 列号 (1-based) */
    private Integer colNum;

    /** 锁座用户ID */
    private Long userId;

    private String lockToken;

    private String orderNo;

    /** 锁定到期时间 */
    private LocalDateTime lockUntil;

    /** 状态: 1=锁定中, 0=已释放, 2=已购买 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
