package com.guangying.service.mq.handler;

import java.util.Map;

public interface OrderEventHandler {

    String eventType();

    void handle(Map<String, Object> event);
}
