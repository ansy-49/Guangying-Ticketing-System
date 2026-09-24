package com.guangying.domain.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 影院列表查询DTO
 */
@Data
public class CinemaQueryDTO implements Serializable {

    /** 所属城市ID */
    private Long cityId;

    /** 日期 (格式: yyyy-M-d) */
    private String day;

    /** 分页偏移量 */
    private Integer offset = 0;

    /** 每页大小 */
    private Integer limit = 20;

    /** 品牌ID (-1=全部) */
    private Long brandId;

    /** 服务ID (-1=全部) */
    private Long serviceId;

    /** 厅型ID (-1=全部) */
    private Long hallType;

    /** 行政区ID (-1=全部) */
    private Long districtId;

    /** 商圈/区域ID (-1=全部) */
    private Long areaId;
}
