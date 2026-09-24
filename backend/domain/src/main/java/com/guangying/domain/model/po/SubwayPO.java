package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 地铁线路/站点持久化对象
 * parent_id=0 表示地铁线路, parent_id>0 表示该线路下的站点
 */
@Data
@TableName("subway")
public class SubwayPO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名称 */
    private String name;

    /** 所属城市ID */
    private Long cityId;

    /** 父级ID (0=地铁线, >0=站点) */
    private Long parentId;

    /** 该站点附近影院数量 */
    private Integer count;
}
