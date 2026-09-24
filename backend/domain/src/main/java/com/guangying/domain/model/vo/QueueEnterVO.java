package com.guangying.domain.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 排队入场结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueueEnterVO implements Serializable {
    /** 是否获得入场资格 */
    private boolean admitted;
    /** 入场令牌（admitted=true 时有效） */
    private String token;
    /** 排队位置（admitted=false 时有效） */
    private int position;
    /** 预估等待秒数（admitted=false 时有效） */
    private int estimatedWaitSeconds;
}
