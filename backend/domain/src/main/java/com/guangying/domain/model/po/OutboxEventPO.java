package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地 Outbox 事件表 — 保证 DB 改了就一定能发出 MQ
 *
 * <p>关键事件（订单创建/超时检查/支付/取消）先写入此表（与业务在同 DB 事务内），
 * 事务提交后由异步线程/定时任务轮询发 MQ，成功后标记 SENT。
 * RocketMQ 宕机时事件不丢，恢复后自动补发。</p>
 */
@Data
@TableName("outbox_event")
public class OutboxEventPO implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局事件 ID，同时作为消费幂等键 */
    private String eventId;

    /** 事件类型: ORDER_CREATED / ORDER_TIMEOUT_CHECK / ORDER_PAID / ORDER_CANCELLED */
    private String eventType;

    /** JSON 载荷 */
    private String payload;

    /** PENDING → PROCESSING → SENT；超过重试上限进入 DEAD */
    private String status;

    private LocalDateTime createTime;
    private LocalDateTime sentTime;
    private LocalDateTime nextRetryTime;
    private String claimToken;
    private LocalDateTime claimedUntil;
    private String lastError;

    /** 重试次数 */
    private Integer retries;
}
