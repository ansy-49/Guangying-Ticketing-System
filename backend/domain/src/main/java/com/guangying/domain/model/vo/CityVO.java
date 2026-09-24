package com.guangying.domain.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 城市视图对象
 */
@Data
public class CityVO implements Serializable {

    private Long id;

    /** 城市名 */
    private String nm;

    /** 拼音 */
    private String py;
}
