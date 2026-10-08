package com.guangying.service.mq;

import com.guangying.common.constants.MQConstants;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 普通订单领域事件消费者。解析、幂等和处理器路由统一交给 OrderEventProcessor。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = MQConstants.ORDER_TOPIC,
        consumerGroup = MQConstants.ORDER_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        selectorExpression = "*"
)
public class OrderEventConsumer implements RocketMQListener<String> {

    private final OrderEventProcessor eventProcessor;

    @Override
    public void onMessage(String message) {
        eventProcessor.process(message);
    }
}
