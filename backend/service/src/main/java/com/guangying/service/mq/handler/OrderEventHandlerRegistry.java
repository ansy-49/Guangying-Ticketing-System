package com.guangying.service.mq.handler;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 命令处理器注册表：按事件类型路由并拒绝重复注册。
 */
@Component
public class OrderEventHandlerRegistry {

    private final Map<String, OrderEventHandler> handlers;

    public OrderEventHandlerRegistry(List<OrderEventHandler> handlerList) {
        Map<String, OrderEventHandler> registered = new LinkedHashMap<>();
        for (OrderEventHandler handler : handlerList) {
            OrderEventHandler previous = registered.put(handler.eventType(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate order-event handler: " + handler.eventType());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    public Optional<OrderEventHandler> find(String eventType) {
        return Optional.ofNullable(handlers.get(eventType));
    }
}
