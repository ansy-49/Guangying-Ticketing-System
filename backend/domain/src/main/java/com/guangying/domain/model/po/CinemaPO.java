package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.guangying.domain.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 影院持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cinema")
public class CinemaPO extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 影院名称 */
    private String nm;

    /** 地址 */
    private String addr;

    /** 所属城市ID */
    private Long cityId;

    /** 品牌ID */
    private Long brandId;

    /** 行政区ID */
    private Long districtId;

    /** 商圈/区域ID */
    private Long areaId;

    /** 距离描述 */
    private String distance;

    /** 是否可退票 */
    private Integer allowRefund;

    /** 是否可改签 */
    private Integer endorse;

    /** 是否有小吃 */
    private Integer snack;

    /** VIP标签 */
    private String vipTag;

    /** 厅型JSON数组 (如 ["IMAX","杜比全景声"]) */
    private String hallTypesJson;

    /** 促销标签 */
    private String cardPromotionTag;

    /** 排序权重 */
    private Integer sortOrder;
}
