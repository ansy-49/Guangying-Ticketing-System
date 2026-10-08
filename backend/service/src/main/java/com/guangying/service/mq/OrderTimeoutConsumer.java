package com.guangying.service.mq;

import com.guangying.common.constants.MQConstants;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 独立消费 RocketMQ 5 定时消息，避免普通事件 Topic 混用不同消息类型。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.name-server")
@RocketMQMessageListener(
        topic = MQConstants.ORDER_TIMEOUT_TOPIC,
        consumerGroup = MQConstants.ORDER_TIMEOUT_CONSUMER_GROUP,
        consumeMode = ConsumeMode.CONCURRENTLY,
        selectorExpression = MQConstants.TAG_ORDER_TIMEOUT_CHECK
)
public class OrderTimeoutConsumer implements RocketMQListener<String> {

    private final OrderEventProcessor eventProcessor;

    @Override
    public void onMessage(String message) {
        eventProcessor.process(message);
    }
}
