package com.guangying.domain.model.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 想看事件 - 通过 MQ 异步写回数据库
 *
 * <p>场景：用户点击"想看" → Redis INCR 实时+1 → 发 MQ → Consumer 批量回写 DB</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishEvent implements Serializable {

    /** 用户ID */
    private Long userId;

    /** 电影ID */
    private Long movieId;

    /** 增量 (+1 / -1) */
    private int delta;

    /** 事件时间戳 */
    private long timestamp;
}
