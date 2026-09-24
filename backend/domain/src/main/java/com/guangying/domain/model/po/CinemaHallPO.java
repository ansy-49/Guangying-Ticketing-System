package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guangying.domain.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 影院影厅 - 座位布局实体
 * <p>
 * 设计理念（大厂思路）：
 * 物理影厅的座位布局信息独立存储，与场次（movie_schedule）通过 cinema_id + hall_name 关联。
 * 支持：行列定义、过道位置、情侣座、不可用座位等复杂布局。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cinema_hall")
public class CinemaHallPO extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 影院ID */
    private Long cinemaId;

    /** 影厅名称(如IMAX厅、3号厅) */
    private String hallName;

    /** 座位总行数 */
    private Integer seatRows;

    /** 座位总列数 */
    private Integer seatCols;

    /** 过道在第N列之后(逗号分隔)，如 "3,11" 表示第3列和第11列后有过道 */
    private String aisleAfterCol;

    /** 情侣座行号(逗号分隔)，如 "10" 表示第10行是情侣座 */
    private String coupleRows;

    /** 不可用座位 JSON 数组 [[row,col],...] */
    private String disabledSeats;

    /** 影厅类型(IMAX/杜比影院/4DX/普通厅等) */
    private String hallType;
}
