package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 城市持久化对象
 */
@Data
@TableName("city")
public class CityPO implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 城市名 */
    private String nm;

    /** 拼音 */
    private String py;
}
