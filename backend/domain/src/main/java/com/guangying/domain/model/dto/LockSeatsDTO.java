package com.guangying.domain.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 锁座请求
 */
@Data
public class LockSeatsDTO implements Serializable {

    /** 客户端为一次购票意图生成的幂等键；网络重试必须复用同一个值 */
    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键长度不能超过64位")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "幂等键格式不正确")
    private String idempotencyKey;

    /** 场次ID */
    @NotNull(message = "场次ID不能为空")
    private Long scheduleId;

    /** 选择的座位列表 */
    @Valid
    @NotEmpty(message = "至少选择一个座位")
    @Size(max = 6, message = "最多选择6个座位")
    private List<SeatPos> seats;

    @Data
    public static class SeatPos implements Serializable {
        /** 行号 (1-based) */
        @NotNull
        @Min(value = 1, message = "座位行号必须大于0")
        private Integer row;

        /** 列号 (1-based) */
        @NotNull
        @Min(value = 1, message = "座位列号必须大于0")
        private Integer col;
    }
}
