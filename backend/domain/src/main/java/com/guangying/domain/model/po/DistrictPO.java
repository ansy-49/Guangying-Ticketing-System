package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 行政区/商圈持久化对象
 * parent_id=0 表示一级行政区, parent_id>0 表示该行政区下的商圈/区域
 */
@Data
@TableName("district")
public class DistrictPO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名称 */
    private String name;

    /** 所属城市ID */
    private Long cityId;

    /** 父级ID (0=顶级行政区) */
    private Long parentId;

    /** 该区域下影院数量 */
    private Integer count;
}
