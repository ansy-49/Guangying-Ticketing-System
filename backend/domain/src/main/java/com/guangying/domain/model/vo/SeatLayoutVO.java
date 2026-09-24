package com.guangying.domain.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 影厅座位布局 VO — 前端渲染座位图使用
 *
 * <p>包含座位网格(rows×cols)、过道位置、每个座位的状态信息</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatLayoutVO implements Serializable {

    /** 影厅名称 */
    private String hallName;

    /** 厅类型 */
    private String hallType;

    /** 总行数 */
    private Integer rows;

    /** 总列数 */
    private Integer cols;

    /** 过道在第N列之后 */
    private List<Integer> aisles;

    /** 情侣座行号列表 */
    private List<Integer> coupleRows;

    /** 座位状态二维数组 seats[row][col] */
    private List<List<SeatInfo>> seats;

    @Data
    public static class SeatInfo implements Serializable {
        /** 行号 */
        private Integer row;
        /** 列号 */
        private Integer col;
        /** 座位标签: "5排3座" */
        private String label;
        /**
         * 座位状态:
         * 0 = 可选
         * 1 = 已售
         * 2 = 已锁定(其他用户)
         * 3 = 我锁定的
         * -1 = 不可用(该位置无座位)
         */
        private Integer status;
        /** 是否情侣座 */
        private Boolean couple;
    }
}
