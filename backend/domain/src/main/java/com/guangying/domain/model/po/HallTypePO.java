package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 影厅类型持久化对象
 */
@Data
@TableName("hall_type")
public class HallTypePO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 厅型名称 */
    private String name;

    /** 拥有该厅型的影院数量 */
    private Integer count;
}
