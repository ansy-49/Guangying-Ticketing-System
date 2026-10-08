package com.guangying.service.mq.handler;

import java.util.Map;

/**
 * 订单领域事件命令处理器。
 *
 * <p>每种事件对应一个处理器，避免消费者随着事件类型增加而不断膨胀。</p>
 */
public interface OrderEventHandler {

    String eventType();

    void handle(Map<String, Object> event);
}
