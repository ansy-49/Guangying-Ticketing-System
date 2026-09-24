package com.guangying.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 排队状态 VO（前端轮询用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusVO implements Serializable {
    /** 是否已获得入场资格 */
    private boolean admitted;
    /** 排队位置 */
    private int position;
    /** 预估等待秒数 */
    private int estimatedWaitSeconds;
}
