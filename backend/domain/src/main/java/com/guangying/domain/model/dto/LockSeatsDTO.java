package com.guangying.domain.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 锁座请求
 */
@Data
public class LockSeatsDTO implements Serializable {

    /** 场次ID */
    @NotNull(message = "场次ID不能为空")
    private Long scheduleId;

    /** 选择的座位列表 */
    @NotEmpty(message = "至少选择一个座位")
    @Size(max = 6, message = "最多选择6个座位")
    private List<SeatPos> seats;

    @Data
    public static class SeatPos implements Serializable {
        /** 行号 (1-based) */
        @NotNull
        private Integer row;

        /** 列号 (1-based) */
        @NotNull
        private Integer col;
    }
}
