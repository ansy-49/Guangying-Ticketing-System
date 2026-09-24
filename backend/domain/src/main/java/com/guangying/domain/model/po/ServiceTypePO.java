package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 影院服务类型持久化对象
 */
@Data
@TableName("service_type")
public class ServiceTypePO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务名称 */
    private String name;

    /** 拥有该服务的影院数量 */
    private Integer count;
}
