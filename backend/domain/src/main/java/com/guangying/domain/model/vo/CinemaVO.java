package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 影院视图对象 — 字段名与前端 CinemaItem 完全一致
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CinemaVO implements Serializable {

    private Long id;

    /** 影院名 */
    private String nm;

    /** 地址 */
    private String addr;

    /** 距离 */
    private String distance;

    /** 标签 */
    private Tag tag;

    /** 促销 */
    private Promotion promotion;

    @Data
    public static class Tag implements Serializable {
        private Boolean allowRefund;
        private Boolean endorse;
        private Boolean snack;
        private String vipTag;
        private List<String> hallType;
    }

    @Data
    public static class Promotion implements Serializable {
        private String cardPromotionTag;
    }
}
