package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.guangying.domain.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 场次持久化对象（防超卖核心表）
 *
 * <p>乐观锁版本号 {@link #version} 用于 DB 层面的最终库存扣减确认，
 * 配合 Redis Lua 脚本预扣库存，实现高并发下的防超卖。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("movie_schedule")
public class SchedulePO extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联电影 */
    private Long movieId;

    /** 关联影院 */
    private Long cinemaId;

    /** 影厅名称 */
    private String hallName;

    /** 放映日期 */
    private String showDate;

    /** 放映时间 HH:mm */
    private String showTime;

    /** 散场时间 */
    private String endTime;

    /** 语言版本 */
    private String lang;

    /** 总座位数 */
    private Integer totalSeats;

    /** 剩余可售座位 */
    private Integer availableSeats;

    /** 单价 */
    private BigDecimal price;

    /** 1=可售 0=停售 */
    private Integer status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
