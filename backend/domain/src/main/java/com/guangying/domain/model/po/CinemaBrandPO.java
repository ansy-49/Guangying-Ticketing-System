package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 影院品牌持久化对象
 */
@Data
@TableName("cinema_brand")
public class CinemaBrandPO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 品牌名称 */
    private String name;

    /** 拥有该品牌的影院数量 */
    private Integer count;
}
