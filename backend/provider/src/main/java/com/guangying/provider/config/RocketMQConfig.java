package com.guangying.provider.config;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置（面试亮点：阿里双11同款 MQ）
 *
 * <p>RocketMQ 由 rocketmq-spring-boot-starter 自动配置：</p>
 * <ul>
 *   <li>Topic 自动创建（autoCreateTopicEnable=true，开发/测试环境）</li>
 *   <li>Producer 自动注册（配置 name-server + group 即可）</li>
 *   <li>Consumer 通过 @RocketMQMessageListener 注解自动注册</li>
 * </ul>
 *
 * <h3>RocketMQ vs RabbitMQ 选型理由：</h3>
 * <pre>
 * 1. 抢座/秒杀场景：单机 10万+ TPS，百万消息堆积不影响性能
 * 2. 事务消息：原生支持（下单+扣库存+占座 事务一致性）
 * 3. 延迟消息：原生支持（15分钟未支付自动取消）
 * 4. 同步刷盘 + 双主双从：金融级不丢消息
 * 5. 经过阿里双11验证，天生为电商/秒杀/抢座而生
 * </pre>
 *
 * <p>仅在配置了 rocketmq.name-server 时激活</p>
 */
@Configuration
@ConditionalOnProperty(name = "rocketmq.name-server")
public class RocketMQConfig {

    // RocketMQ Spring Boot Starter 自动配置了：
    // 1. RocketMQTemplate（生产者模板，类似 RabbitTemplate）
    // 2. DefaultMQProducer（底层生产者实例）
    // 3. @RocketMQMessageListener 标注的消费者自动注册
    //
    // 无需手动声明 Queue/Exchange/Binding（不同于 RabbitMQ）
    // RocketMQ 的 Topic 在 Broker 端自动创建或通过控制台预创建
}
