package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 电影视图对象 — 字段名与前端 MovieItem 完全一致
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MovieVO implements Serializable {

    private Long id;

    /** 电影名 */
    private String nm;

    /** 英文名 */
    private String enm;

    /** 海报图 */
    private String img;

    /** 评分 */
    private Object sc;

    /** 演员 */
    private String star;

    /** 分类标签 */
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

    /** 是否已全球上映 */
    private Boolean globalReleased;

    /** 上映年份 */
    private Integer releaseYear;

    /** 上映信息 */
    private String showInfo;

    /** 即将上映标题 */
    private String comingTitle;

    /** 视频URL */
    private String vd;

    /** 剧照列表 */
    private List<String> photos;

    /** 剧照总数 */
    private Integer pn;
}
