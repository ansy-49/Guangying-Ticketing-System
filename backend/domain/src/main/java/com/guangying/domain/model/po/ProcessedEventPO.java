package com.guangying.domain.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** RocketMQ 消费幂等记录。 */
@Data
@TableName("processed_event")
public class ProcessedEventPO implements Serializable {

    @TableId(type = IdType.INPUT)
    private String eventId;
    private String eventType;
    private String orderNo;
    private LocalDateTime processedTime;
}
