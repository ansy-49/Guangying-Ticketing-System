package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.guangying.domain.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 电影持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("movie")
public class MoviePO extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 电影名 */
    private String nm;

    /** 英文名 */
    private String enm;

    /** 海报图URL */
    private String img;

    /** 评分 */
    private BigDecimal sc;

    /** 演员 */
    private String star;

    /** 分类标签 (如 "剧情,科幻") */
    private String cat;

    /** 来源/国家 */
    private String src;

    /** 时长(分钟) */
    private Integer dur;

    /** 上映描述 */
    private String pubDesc;

    /** 剧情简介 */
    private String dra;

    /** 想看人数 */
    private Integer wish;

    /** 视频URL */
    private String vd;

    /** 剧照JSON数组 */
    private String photos;

    /** 剧照总数 */
    private Integer pn;

    /** 上映信息 (如 "今天28家影院放映356场") */
    private String showInfo;

    /** 即将上映标题 (如 "3月15日 周六") */
    private String comingTitle;

    /** 电影状态: 0=即将上映, 1=正在热映, 2=已下映 */
    private Integer movieStatus;

    /** 是否已全球上映: 0=否, 1=是 */
    private Integer globalReleased;

    /** 上映年份 */
    private Integer releaseYear;

    /** 排序权重 */
    private Integer sortOrder;

    /** 乐观锁版本号 */
    @Version
    private Integer version;
}
